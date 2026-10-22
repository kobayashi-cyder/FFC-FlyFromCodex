from pathlib import Path
from zipfile import ZipFile
import struct
import sys

def ui_text(text):
    if 'BANC' not in text:
        return text
    label='自作回路' if any('\u3040'<=c<='\u9fff' for c in text) else 'Custom circuit'
    return text.replace('BANC888',label).replace('BANC',label)

def rewrite_string_pool(chunk):
    kind,header,size=struct.unpack_from('<HHI',chunk)
    count,styles,flags,start,style_start=struct.unpack_from('<5I',chunk,8)
    utf8=bool(flags&256)
    def read8(at):
        value=chunk[at]
        return (((value&127)<<8)|chunk[at+1],at+2) if value&128 else (value,at+1)
    def read16(at):
        value=struct.unpack_from('<H',chunk,at)[0]
        return (((value&32767)<<16)|struct.unpack_from('<H',chunk,at+2)[0],at+4) if value&32768 else (value,at+2)
    def write8(value):
        if value>32767:raise ValueError('UTF8 string too long')
        return bytes([128|(value>>8),value&255]) if value>127 else bytes([value])
    def write16(value):
        return struct.pack('<HH',32768|(value>>16),value&65535) if value>32767 else struct.pack('<H',value)
    offsets=[];payload=bytearray()
    for index in range(count):
        at=start+struct.unpack_from('<I',chunk,header+index*4)[0]
        if utf8:
            _,at=read8(at);length,at=read8(at)
        else:
            length,at=read16(at);length*=2
        encoding='utf-8' if utf8 else 'utf-16le'
        text=ui_text(chunk[at:at+length].decode(encoding))
        encoded=text.encode(encoding);units=len(text.encode('utf-16le'))//2
        offsets.append(len(payload))
        payload.extend(write8(units)+write8(len(encoded))+encoded+b'\0' if utf8 else write16(units)+encoded+b'\0\0')
    payload.extend(b'\0'*(-len(payload)%4))
    style_offsets=chunk[header+count*4:header+(count+styles)*4]
    style_data=chunk[style_start:] if style_start else b''
    new_start=header+4*(count+styles)
    new_style_start=new_start+len(payload) if style_start else 0
    new_header=bytearray(chunk[:header]);struct.pack_into('<I',new_header,4,new_start+len(payload)+len(style_data))
    struct.pack_into('<3I',new_header,16,flags&~1,new_start,new_style_start)
    return bytes(new_header)+b''.join(struct.pack('<I',value) for value in offsets)+style_offsets+payload+style_data

def rewrite_resources(data):
    header=struct.unpack_from('<H',data,2)[0];parts=[data[:header]];pos=header
    while pos<len(data):
        kind,_,size=struct.unpack_from('<HHI',data,pos)
        chunk=data[pos:pos+size]
        parts.append(rewrite_string_pool(chunk) if kind==1 else chunk)
        pos+=size
    output=bytearray(b''.join(parts));struct.pack_into('<I',output,4,len(output));return output

def manifest_version(data, code=135, version="1.132"):
    data=bytearray(data)
    strings=[]
    locations=[]
    changed=set()
    def length8(at):
        value=data[at]
        return (((value&127)<<8)|data[at+1],at+2) if value&128 else (value,at+1)
    def length16(at):
        value=struct.unpack_from("<H",data,at)[0]
        return (((value&32767)<<16)|struct.unpack_from("<H",data,at+2)[0],at+4) if value&32768 else (value,at+2)
    pos=8
    while pos<len(data):
        kind,header,size=struct.unpack_from("<HHI",data,pos)
        if size<header or pos+size>len(data):
            raise ValueError("invalid XML chunk")
        if kind==1:
            count,styles,flags,start=struct.unpack_from("<4I",data,pos+8)
            encoding="utf-8" if flags&256 else "utf-16le"
            for i in range(count):
                at=pos+start+struct.unpack_from("<I",data,pos+header+i*4)[0]
                if flags&256:
                    _,at=length8(at)
                    length,at=length8(at)
                else:
                    length,at=length16(at)
                    length*=2
                strings.append(bytes(data[at:at+length]).decode(encoding))
                locations.append((at,length,encoding))
        if kind==0x102:
            node=struct.unpack_from("<I",data,pos+20)[0]
            attr_start,attr_size,count=struct.unpack_from("<3H",data,pos+24)
            if strings[node]=="manifest":
                for i in range(count):
                    at=pos+16+attr_start+i*attr_size
                    ns,name,raw=struct.unpack_from("<3I",data,at)
                    if ns==0xffffffff or strings[ns]!="http://schemas.android.com/apk/res/android":
                        continue
                    if strings[name]=="versionCode":
                        if struct.unpack_from("<I",data,at+16)[0]!=126:
                            raise ValueError("base APK is not version 1.123 (code 126)")
                        struct.pack_into("<I",data,at+16,code)
                        changed.add("code")
                    elif strings[name]=="versionName":
                        index=struct.unpack_from("<I",data,at+16)[0]
                        if strings[index]!="1.123":
                            raise ValueError("unexpected base version name")
                        location,length,encoding=locations[index]
                        replacement=version.encode(encoding)
                        if len(replacement)!=length:
                            raise ValueError("version name must retain its encoded length")
                        data[location:location+length]=replacement
                        changed.add("name")
        pos+=size
    if changed!={"code","name"}:
        raise ValueError("version attributes not found")
    return data

def package(base,dex,output):
    with ZipFile(base) as source,ZipFile(output,"w") as target:
        for entry in source.infolist():
            name=entry.filename
            if name in {'assets/circuit.bin','assets/provenance.json','assets/banc888-recovery-provenance.json'}:
                continue
            if name.upper().startswith("META-INF/") and (name.upper().endswith((".SF",".RSA",".DSA",".EC")) or name.upper()=="META-INF/MANIFEST.MF"):
                continue
            contents=source.read(entry)
            if name=="classes.dex":
                contents=Path(dex).read_bytes()
            elif name=="AndroidManifest.xml":
                contents=manifest_version(contents)
            elif name=='resources.arsc':
                contents=rewrite_resources(contents)
            target.writestr(entry,contents)
        assets=Path(__file__).resolve().parent.parent/'assets'
        for path in sorted(assets.iterdir()):
            if path.is_file():target.write(path,'assets/'+path.name)

if __name__=="__main__":
    package(*sys.argv[1:])
