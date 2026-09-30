#!/usr/bin/env python3
from __future__ import annotations
import argparse, csv, json, hashlib
from pathlib import Path

GROUP_SUFFIX = {
    "BASE":"clean standard view, stable geometry, natural proportions",
    "CLOSEUP":"close-up view with local surface detail",
    "MATERIAL":"emphasize believable material and surface response",
    "LIGHT":"natural controlled lighting and readable volume",
    "DEPTH":"strong foreground, middle-ground and background separation",
    "COMPOSITION":"balanced composition and clear focal structure",
    "CONTEXT":"believable context and scale",
    "O2_MATCH":"preserve semantic layout exactly for O2 comparison",
    "WIDE":"wide scenic framing with expansive composition",
    "TIME":"time-of-day lighting clearly expressed",
    "WEATHER":"distinct believable weather and atmosphere",
    "ATMOSPHERE":"haze, glow, reflection or fog where appropriate",
    "PORTRAIT":"portrait framing with strong facial or subject detail",
    "FULLBODY":"full body visible with readable silhouette",
    "ACTION":"believable motion or dynamic pose",
    "HABITAT":"subject placed in a suitable environment",
    "POSE":"natural pose and joint relationships",
    "CLOTHING":"clear clothing layers and fabric detail",
    "SILHOUETTE":"strong readable silhouette",
    "SHADOW":"natural cast and contact shadows",
    "TABLETOP":"natural tabletop placement and scale",
    "DETAIL":"high local detail without geometry drift",
    "FACADE":"clear facade rhythm, openings and symmetry",
    "INTERIOR":"coherent room depth and furniture scale",
    "PERSPECTIVE":"correct linear perspective and vanishing behavior",
    "DAYLIGHT":"natural daylight and directional illumination",
    "NIGHT":"night lighting and local glow",
    "SIDE":"clear side view and readable profile",
    "FRONT":"front or three-quarter view with coherent symmetry",
    "REFLECTION":"controlled reflections on reflective surfaces",
    "MACRO":"macro close-up with fine small-scale detail",
    "BODY_SHAPE":"accurate body segmentation and silhouette",
    "WING":"wing structure, transparency and symmetry",
    "LEGS":"coherent leg count and attachment",
    "GLOW":"controlled luminous glow",
    "NODE_DENSITY":"balanced node density and cluster spacing",
    "LINK_COMPLEXITY":"rich but readable connectivity",
    "COLOR":"coherent limited color palette",
    "FORM":"preserve intended silhouette while retaining graph character",
    "ABSTRACT":"abstract scientific visualization with elegant rhythm",
}

# tuple: subcategory, concept, prompt, focus_tags, repair_targets
DOMAINS = {
"FOOD": {
 "groups":["BASE","CLOSEUP","MATERIAL","LIGHT","DEPTH","COMPOSITION","CONTEXT","O2_MATCH"],
 "concepts":[
  ("FOOD_JAPANESE","ramen","a bowl of ramen with broth, noodles, toppings and rising steam",["broth","noodle texture","steam","bowl reflection"],["surface","lighting","shape"]),
  ("FOOD_JAPANESE","sushi","a sushi platter with neatly arranged sushi pieces on a plate",["rice texture","fish gloss","plate composition"],["surface","composition","shape"]),
  ("FOOD_JAPANESE","curry_rice","a plate of japanese curry rice with rich sauce and white rice",["sauce gloss","rice grains","plate balance"],["surface","palette","composition"]),
  ("FOOD_DRINK","coffee","a cup of coffee on a table with warm ambience",["liquid reflection","cup material","table texture"],["surface","lighting","material"]),
  ("FOOD_DESSERT","cake","a slice of cake on a plate with soft dessert presentation",["cream texture","plate highlight","sweet color balance"],["surface","palette","composition"]),
 ]},
"LANDSCAPE": {
 "groups":["BASE","WIDE","DEPTH","TIME","WEATHER","ATMOSPHERE","COMPOSITION","O2_MATCH"],
 "concepts":[
  ("LAND_MOUNTAIN","mountain_sunrise","a mountain landscape at sunrise with layered peaks and open sky",["sky gradient","mountain layering","morning light"],["depth","atmosphere","palette"]),
  ("LAND_SEA","sunset_beach","a beach at sunset with sea, shore and glowing sky",["water reflection","sunset palette","shore composition"],["palette","depth","lighting"]),
  ("LAND_FOREST","forest_path","a forest path with trees and a path leading into the distance",["path perspective","foliage texture","light shafts"],["depth","composition","surface"]),
  ("LAND_LAKE","lake_reflection","a calm lake with reflective water and distant scenery",["reflection","water surface","distance haze"],["surface","depth","atmosphere"]),
  ("LAND_CITY","city_night","a cityscape at night with lights and urban atmosphere",["night lights","building silhouettes","glow"],["lighting","depth","composition"]),
 ]},
"ANIMAL": {
 "groups":["BASE","PORTRAIT","FULLBODY","ACTION","HABITAT","MATERIAL","LIGHT","O2_MATCH"],
 "concepts":[
  ("ANIMAL_PET","cat","a cat with clear face, body and fur details",["fur","face structure","eye placement"],["shape","surface","anatomy"]),
  ("ANIMAL_PET","dog","a dog with clear body shape and natural fur texture",["fur","muzzle","pose"],["shape","surface","anatomy"]),
  ("ANIMAL_BIRD","bird","a bird with feathers, clear silhouette and natural pose",["feather layering","beak shape","pose"],["shape","surface","anatomy"]),
  ("ANIMAL_FARM","horse","a horse with readable full body anatomy in a natural pose",["leg structure","body proportion","mane"],["shape","anatomy","composition"]),
  ("ANIMAL_INSECT","butterfly","a butterfly with wing details and delicate body structure",["wing symmetry","pattern","macro detail"],["shape","surface","detail"]),
 ]},
"PERSON": {
 "groups":["BASE","PORTRAIT","FULLBODY","POSE","CLOTHING","LIGHT","CONTEXT","O2_MATCH"],
 "concepts":[
  ("PERSON_SINGLE","adult_portrait","a single adult person with a natural face and coherent features",["face","eyes","hair"],["anatomy","surface","shape"]),
  ("PERSON_SINGLE","standing_person","a person standing naturally with full body visible",["body proportion","legs","posture"],["anatomy","shape","composition"]),
  ("PERSON_ACTION","walking_person","a person walking in a natural stride",["gait","limb relation","balance"],["anatomy","pose","relations"]),
  ("PERSON_PAIR","two_people","two people standing together with clear separation and natural interaction",["identity separation","relative scale","pose"],["composition","relations","anatomy"]),
  ("PERSON_PORTRAIT","face_closeup","a close-up human face with balanced features and realistic skin",["eyes","nose","mouth","skin"],["shape","surface","anatomy"]),
 ]},
"OBJECT": {
 "groups":["BASE","SILHOUETTE","MATERIAL","CLOSEUP","SHADOW","TABLETOP","DETAIL","O2_MATCH"],
 "concepts":[
  ("OBJECT_FURNITURE","wooden_chair","a wooden chair with clear construction and natural proportions",["wood grain","legs","seat"],["shape","surface","material"]),
  ("OBJECT_TABLEWARE","glass_cup","a clear glass cup on a table",["transparency","reflection","rim"],["material","lighting","shape"]),
  ("OBJECT_BOOK","closed_book","a closed book with visible cover, pages and spine",["edges","paper","cover material"],["shape","surface","detail"]),
  ("OBJECT_LAMP","desk_lamp","a desk lamp with a clear metal body and light source",["metal","light source","joint structure"],["material","lighting","shape"]),
  ("OBJECT_PLANT","potted_plant","a potted plant with leaves and ceramic pot",["leaf texture","pot material","stem layout"],["surface","shape","material"]),
 ]},
"ARCHITECTURE": {
 "groups":["BASE","FACADE","INTERIOR","PERSPECTIVE","MATERIAL","DAYLIGHT","NIGHT","O2_MATCH"],
 "concepts":[
  ("ARCH_HOUSE","modern_house","a modern house with clear facade, windows and entrance",["facade","windows","roofline"],["perspective","shape","material"]),
  ("ARCH_TRADITIONAL","traditional_japanese_house","a traditional japanese house with wood structure and tiled roof",["wood structure","roof tiles","proportion"],["shape","material","perspective"]),
  ("ARCH_PUBLIC","temple","a temple building with symmetrical composition and architectural details",["symmetry","roof","columns"],["shape","composition","perspective"]),
  ("ARCH_CITY","office_building","a modern office building with glass facade and urban context",["glass","vertical lines","window grid"],["material","perspective","detail"]),
  ("ARCH_INTERIOR","living_room","a living room interior with furniture, windows and coherent room perspective",["room depth","furniture scale","window light"],["depth","composition","perspective"]),
 ]},
"VEHICLE": {
 "groups":["BASE","SIDE","FRONT","ACTION","MATERIAL","REFLECTION","NIGHT","O2_MATCH"],
 "concepts":[
  ("VEHICLE_CAR","compact_car","a compact car with coherent body, windows and wheels",["body shape","wheel placement","glass"],["shape","material","perspective"]),
  ("VEHICLE_CYCLE","bicycle","a bicycle with clear frame geometry and two wheels",["frame","wheels","handlebar"],["shape","detail","perspective"]),
  ("VEHICLE_RAIL","train","a modern train with clear body, windows and rail context",["long body","windows","rail perspective"],["shape","perspective","composition"]),
  ("VEHICLE_AIR","airplane","a passenger airplane with coherent wings, fuselage and tail",["wing symmetry","fuselage","landing context"],["shape","symmetry","perspective"]),
  ("VEHICLE_SHIP","boat","a small boat on water with clear hull and reflections",["hull","water reflection","scale"],["shape","surface","depth"]),
 ]},
"INSECT": {
 "groups":["BASE","MACRO","BODY_SHAPE","WING","LEGS","MATERIAL","HABITAT","O2_MATCH"],
 "concepts":[
  ("INSECT_FLY","fly","a fly with clear head, thorax, abdomen, wings and six legs",["body segmentation","wing transparency","leg placement"],["shape","detail","anatomy"]),
  ("INSECT_BEE","bee","a bee with striped abdomen, wings and fuzzy body",["fuzz","stripes","wing structure"],["surface","shape","detail"]),
  ("INSECT_BUTTERFLY","butterfly_macro","a butterfly with symmetrical patterned wings and delicate body",["wing symmetry","pattern","antennae"],["shape","surface","symmetry"]),
  ("INSECT_DRAGONFLY","dragonfly","a dragonfly with long body and four transparent wings",["wing geometry","long abdomen","leg placement"],["shape","detail","symmetry"]),
  ("INSECT_BEETLE","beetle","a beetle with hard shell, segmented legs and compact body",["shell reflection","body segmentation","legs"],["material","shape","detail"]),
 ]},
"CONNECTOME_STYLE": {
 "groups":["BASE","GLOW","NODE_DENSITY","LINK_COMPLEXITY","COLOR","FORM","ABSTRACT","O2_MATCH"],
 "concepts":[
  ("CONNECTOME_GRAPH","neural_node_cluster","a dense luminous neural node cluster connected by fine lines on a dark background",["node spacing","link density","glow"],["composition","detail","lighting"]),
  ("CONNECTOME_INSECT","glowing_connectome_insect","an insect-like form constructed from glowing connectome nodes and fine neural links",["insect silhouette","graph structure","glow"],["shape","composition","lighting"]),
  ("CONNECTOME_LAND","connectome_landscape","a landscape interpreted as a glowing connectome network of nodes and links",["terrain silhouette","network density","depth"],["composition","depth","detail"]),
  ("CONNECTOME_FLORA","connectome_flower","a flower-like form made of luminous nodes and branching connectome links",["radial form","branching","glow"],["shape","detail","lighting"]),
  ("CONNECTOME_FACE","connectome_face_pattern","a face-like pattern emerging from a dense network of glowing nodes and links",["face suggestion","node distribution","line flow"],["composition","shape","detail"]),
 ]},
}

def build_jobs(seeds=(1111,2222), cfgs=(6,8), steps=(24,), samplers=("DPM++ 2M",), sizes=((768,512),(512,512))):
    jobs=[]; n=0
    for domain, lib in DOMAINS.items():
        for subcat, concept, base_prompt, focus, repair0 in lib["concepts"]:
            for group in lib["groups"]:
                suffix=GROUP_SUFFIX[group]
                compare=(["semantic","relations"] if group=="O2_MATCH"
                         else ["composition","depth"] if group in {"COMPOSITION","DEPTH","WIDE","PERSPECTIVE"}
                         else ["surface","shape"])
                repair=list(dict.fromkeys(repair0 + ([] if group in {"BASE","O2_MATCH"} else [group.lower()])))
                for seed in seeds:
                    for cfg in cfgs:
                        for st in steps:
                            for sampler in samplers:
                                for w,h in sizes:
                                    n+=1
                                    safe_sampler=sampler.replace(" ","_").replace("+","p")
                                    filename=f"P{n:05d}_{domain}_{subcat}_{concept}_{group}_s{seed}_c{cfg}_st{st}_{safe_sampler}_{w}x{h}.png"
                                    jobs.append({
                                        "id":f"P{n:05d}","domain":domain,"subcategory":subcat,"concept":concept,"group":group,
                                        "prompt":f"{base_prompt}, {suffix}",
                                        "negative_prompt":"distorted geometry, blurry detail, inconsistent composition, watermark",
                                        "focus_tags":focus,"compare_targets":compare,"repair_targets":repair,
                                        "seed":seed,"cfg_scale":cfg,"steps":st,"sampler_name":sampler,"scheduler":"Automatic",
                                        "width":w,"height":h,"endpoint":"/sdapi/v1/txt2img","filename":filename,
                                        "compare_against":f"O2 {domain} same concept and group"
                                    })
    return jobs

def validate(jobs):
    errors=[]
    expected_domains=set(DOMAINS)
    got_domains={j["domain"] for j in jobs}
    if got_domains != expected_domains: errors.append(f"domain mismatch {got_domains ^ expected_domains}")
    expected_concepts=sum(len(d["concepts"]) for d in DOMAINS.values())
    got_concepts=len({(j["domain"],j["subcategory"],j["concept"]) for j in jobs})
    if got_concepts != expected_concepts: errors.append(f"concept count {got_concepts} != {expected_concepts}")
    expected_default=expected_concepts*8*2*2*1*1*2
    if len(jobs) != expected_default: errors.append(f"job count {len(jobs)} != {expected_default}")
    if len({j["id"] for j in jobs}) != len(jobs): errors.append("duplicate ids")
    if len({j["filename"] for j in jobs}) != len(jobs): errors.append("duplicate filenames")
    for j in jobs:
        if not j["prompt"] or not j["negative_prompt"]: errors.append(f"empty prompt {j['id']}")
        if not j["focus_tags"] or not j["repair_targets"]: errors.append(f"missing evaluation tags {j['id']}")
        if j["endpoint"] != "/sdapi/v1/txt2img": errors.append(f"bad endpoint {j['id']}")
    return errors

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--out", default="dist")
    args=ap.parse_args()
    out=Path(args.out); out.mkdir(parents=True,exist_ok=True)
    jobs=build_jobs()
    errors=validate(jobs)
    bundle={"format":"BANC888-domain-output-pack-v0.3","domains":list(DOMAINS),"total_concepts":sum(len(d["concepts"]) for d in DOMAINS.values()),"total_jobs":len(jobs),"jobs":jobs}
    data=json.dumps(bundle,ensure_ascii=False,indent=2)
    (out/"BANC888_All9Domains_ExecutedLists.json").write_text(data,encoding="utf-8")
    cols=["id","domain","subcategory","concept","group","seed","cfg_scale","steps","sampler_name","width","height","filename","prompt","negative_prompt","focus_tags","compare_targets","repair_targets"]
    with (out/"BANC888_All9Domains_ExecutedLists.csv").open("w",encoding="utf-8-sig",newline="") as f:
        wr=csv.writer(f); wr.writerow(cols)
        for j in jobs: wr.writerow([("|".join(j[k]) if isinstance(j.get(k),list) else j.get(k,"")) for k in cols])
    summary={
        "status":"PASS" if not errors else "FAIL","errors":errors,
        "domains":len(DOMAINS),"concepts":bundle["total_concepts"],"jobs":len(jobs),
        "json_sha256":hashlib.sha256(data.encode()).hexdigest(),
        "per_domain":{d:sum(1 for j in jobs if j["domain"]==d) for d in DOMAINS},
    }
    (out/"evaluation_report.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding="utf-8")
    md=["# BANC888 Domain Pack Evaluation","",f"- Status: **{summary['status']}**",f"- Domains: {summary['domains']}",f"- Concepts: {summary['concepts']}",f"- Jobs: {summary['jobs']}","", "## Per-domain jobs"]
    md += [f"- {k}: {v}" for k,v in summary["per_domain"].items()]
    if errors: md += ["","## Errors"]+[f"- {e}" for e in errors]
    (out/"evaluation_report.md").write_text("\n".join(md)+"\n",encoding="utf-8")
    print(json.dumps(summary,ensure_ascii=False,indent=2))
    raise SystemExit(1 if errors else 0)

if __name__=="__main__":
    main()
