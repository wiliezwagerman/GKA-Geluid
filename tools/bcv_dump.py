#!/usr/bin/env python3
"""Leest een Biamp Canvas-bestand (.bcv) en toont per pagina de bedieningselementen
met label, instance tag en kanaal/preset. Hulpmiddel om layout.json bij te werken.

Gebruik: python3 tools/bcv_dump.py BESTAND.bcv

Let op: het .bcv-formaat is niet gedocumenteerd; dit is een benadering.
'p=' is de kanaalindex (0-based, dus TTP-kanaal = p+1) bij faders/mutes en het
preset-ID bij presetknoppen. Labels horen bij de fader eronder (zelfde x-positie).
"""
import re,sys,struct,json
import zlib
raw=open(sys.argv[1],'rb').read()
d=zlib.decompress(raw[5:]) if raw[5:7]==b'x\x9c' else raw
ctrl_re=re.compile(rb'([\x08-\x20])((?:Button|Paging|Meter|Edit|Fader|Toggle Button|Static|Graph|LED) Control\d+)')
starts=[(m.start(),m.group(2).decode()) for m in ctrl_re.finditer(d) if m.group(1)[0]==len(m.group(2))]
PAGES=['Kerkzaal','Outputs','EQ Kansel','EQ Headset','EQ Tafel 1','EQ Tafel 2','EQ Handheld 1','EQ Handheld 2','EQ Beamer PC','EQ Laptop Extern',"Extra's",'Torenzaal','Outputs TZ',"Extra's TZ",'EQ Kansel TZ','EQ Headset TZ','EQ Orgel TZ','EQ FeedbackSuppressor']
def lstr(seg):
    out=[];i=0
    while i<len(seg)-1:
        n=seg[i]
        if 2<=n<80 and i+1+n<=len(seg):
            s=seg[i+1:i+1+n]
            if all(32<=c<127 or c in(9,10,13) or c>=0xc0 for c in s) and re.match(rb'[A-Za-z0-9%]',s):
                out.append((i,s.decode('latin1').replace('\r\n',' ').strip()));i+=1+n;continue
        i+=1
    return out
tagre=re.compile(r'^(Level|Mute|AudioMeter|LogicMeter|ParametricEQ|FeedbackSuppressor|Input)\d+$')
res=[];page='Kerkzaal'
for k,(off,name) in enumerate(starts):
    end=starts[k+1][0] if k+1<len(starts) else len(d)
    seg=d[off:end]
    x1,y1,x2,y2=struct.unpack('<4i',seg[1+len(name):17+len(name)])
    toks=[t for t in lstr(seg)[1:] if t[1] not in('911','Tahoma')]
    typ=name.rsplit(' Control',1)[0]
    tag=None;param=None;label=None
    if typ=='Paging':
        names=[t[1] for t in toks if t[1] in PAGES]
        nxt=names[18:] if len(names)>18 else []
        if len(names)>18 and names[0] not in PAGES[:1]: pass
        res.append(dict(page=page,type=typ,name=name,rect=(x1,y1,x2,y2)))
        # page change marker = trailing page token beyond the 18 list
        tl=[t[1] for t in toks]
        idx=tl.index('Paging')
        rest=tl[idx+1:]
        continue
    for p,s in toks:
        if tagre.match(s) and tag is None:
            tag=s; tp=p
    if tag:
        j=seg.find(b'\xff'*8,tp)
        if j>4:
            param=struct.unpack('<i',seg[j-4:j])[0]
            if param==0x31313982: param=struct.unpack('<i',seg[j-8:j-4])[0]
    else:
        j=seg.find(b'\xff'*8,0x100)
        if j>4: param=struct.unpack('<i',seg[j-4:j])[0]
    label=toks[0][1] if toks else None
    tl=[t[1] for t in toks]
    res.append(dict(page=page,type=typ,name=name,label=label,tag=tag,param=param,rect=(x1,y1,x2,y2),toks=tl))
    for s_ in tl[2:]:
        if s_ in PAGES and s_!=label:
            page=s_;break
from collections import defaultdict
bp=defaultdict(list)
for r in res: bp[r['page']].append(r)
for p in bp:
    print('==',p, len(bp[p]))
    for r in sorted(bp[p],key=lambda r:(r['rect'][0],r['rect'][1])):
        if r['type']=='Paging': continue
        print('  %-14s %-22s %-20s p=%s rect=%s'%(r['type'],(r.get('label') or '')[:22],r.get('tag'),r.get('param'),r['rect']))
