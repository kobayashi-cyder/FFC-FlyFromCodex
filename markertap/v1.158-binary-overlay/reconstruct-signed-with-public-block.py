#!/usr/bin/env python3
"""Reconstruct v1.158 original-key-signed APK with a PUBLIC v2/v3 signature block.
The RSA private signing key is NOT included in repository or workflow.
Signature block is valid only for the byte-exact unsigned candidate.
"""
import argparse,base64,hashlib,pathlib,struct,zipfile,json
EXPECTED_UNSIGNED='7aaa5df0637d1835f792b75a514c0ced430302998eb29c244df7e0ee1d4b5c0e'
EXPECTED_BLOCK='01ca2cf5f3efef2cb027beb55b13d9a7c42f9e456e4a1b906289eb4bc56bd4a1'
EXPECTED_SIGNED='56e13b2249fb9ce8c98f498498ea14146ddf4095307707c6911214f2f5463ef8'
EXPECTED_CERT='7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1'
def sha(b): return hashlib.sha256(b).hexdigest()
def eocd(b):
    p=b.rfind(b'PK\x05\x06',max(0,len(b)-22-65535))
    if p<0 or p+22+struct.unpack_from('<H',b,p+20)[0]!=len(b): raise ValueError('Invalid EOCD')
    return p
def reconstruct(source,public_block,dest):
    src=pathlib.Path(source).read_bytes()
    block=base64.b64decode(pathlib.Path(public_block).read_text().strip(),validate=True)
    assert sha(src)==EXPECTED_UNSIGNED,'Wrong candidate: stop before applying public signature'
    assert sha(block)==EXPECTED_BLOCK,'Wrong signed block'
    assert block[-16:]==b'APK Sig Block 42'
    assert struct.unpack_from('<Q',block)[0]==len(block)-8
    assert struct.unpack_from('<Q',block,len(block)-24)[0]==len(block)-8
    eo=eocd(src);co=struct.unpack_from('<I',src,eo+16)[0];cs=struct.unpack_from('<I',src,eo+12)[0]
    assert co+cs==eo,'Unexpected ZIP layout'
    end=bytearray(src[eo:]);struct.pack_into('<I',end,16,co+len(block))
    signed=src[:co]+block+src[co:eo]+end
    assert sha(signed)==EXPECTED_SIGNED,'Reconstructed APK differs from signed reference'
    pathlib.Path(dest).parent.mkdir(parents=True,exist_ok=True)
    pathlib.Path(dest).write_bytes(signed)
    with zipfile.ZipFile(dest) as z: assert z.testzip() is None,'ZIP CRC failure'
    report={'version_name':'1.158','version_code':161,'package':'jp.tapmarker.lite','unsigned_sha256':sha(src),'signed_sha256':sha(signed),'signature_block_sha256':sha(block),'signing_certificate_sha256':EXPECTED_CERT,'signing_schemes':['v2','v3'],'real_device_tested':False,'zip_crc_passed':True}
    pathlib.Path(dest).with_suffix('.validation.json').write_text(json.dumps(report,indent=2)+'\n')
    print('reconstructed_and_byte_verified_sha256='+sha(signed))
if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('unsigned_apk');p.add_argument('public_signature_block');p.add_argument('output_apk')
    a=p.parse_args()
    reconstruct(a.unsigned_apk,a.public_signature_block,a.output_apk)
