import struct, sys, collections

TYPE_SIZE = {4: 1, 5: 2, 6: 4, 7: 8, 8: 1, 9: 2, 10: 4, 11: 8}


class H:
    def __init__(self, path):
        self.d = open(path, 'rb').read()
        self.strings = {}
        self.cls_name_sid = {}
        self.cls = {}          # cid -> (superid, [(fname,type)])
        self.obj_cls = {}      # oid -> cid
        self.instances = []    # (oid, cid, dataOffset, nbytes)
        self.parse()

    def rid(self, p):
        return struct.unpack_from('>I' if self.idsize == 4 else '>Q', self.d, p)[0]

    def cname(self, cid):
        return self.strings.get(self.cls_name_sid.get(cid), 'unknown@0x%x' % (cid or 0))

    def parse(self):
        d = self.d
        p = d.index(b'\0') + 1
        self.idsize, = struct.unpack_from('>I', d, p); p += 4 + 8
        n = len(d)
        while p + 9 <= n:
            tag = d[p]
            ln, = struct.unpack_from('>I', d, p + 5)
            p += 9
            end = p + ln
            if tag == 0x01:
                self.strings[self.rid(p)] = d[p + self.idsize:end].decode('utf-8', 'replace')
            elif tag == 0x02:
                q = p + 4
                cid = self.rid(q); q += self.idsize + 4
                self.cls_name_sid[cid] = self.rid(q)
            elif tag in (0x0C, 0x1C):
                self.heap(p, end)
            p = end

    def heap(self, p, end):
        d, ids = self.d, self.idsize
        while p < end:
            t = d[p]; p += 1
            if t == 0xFF: p += ids
            elif t == 0x01: p += ids * 2
            elif t == 0x08: p += ids + 8
            elif t in (0x02, 0x03): p += ids + 8
            elif t in (0x04, 0x06): p += ids + 4
            elif t in (0x05, 0x07, 0xC3, 0x89, 0x8A, 0x8B, 0x8D): p += ids
            elif t == 0x8E: p += ids + 8
            elif t == 0x20: p = self.class_dump(p)
            elif t == 0x21:
                oid = self.rid(p); p += ids + 4
                cid = self.rid(p); p += ids
                nb, = struct.unpack_from('>I', d, p); p += 4
                self.obj_cls[oid] = cid
                self.instances.append((oid, cid, p, nb))
                p += nb
            elif t == 0x22:
                oid = self.rid(p); p += ids + 4
                ne, = struct.unpack_from('>I', d, p); p += 4
                cid = self.rid(p); p += ids
                self.obj_cls[oid] = cid
                p += ne * ids
            elif t == 0x23:
                oid = self.rid(p); p += ids + 4
                ne, = struct.unpack_from('>I', d, p); p += 4
                et = d[p]; p += 1
                self.obj_cls[oid] = None
                p += ne * TYPE_SIZE.get(et, 1)
            else:
                raise ValueError('subtag 0x%x' % t)
        return p

    def class_dump(self, p):
        d, ids = self.d, self.idsize
        cid = self.rid(p); p += ids + 4
        sup = self.rid(p); p += ids
        p += ids * 5 + 4
        ncp, = struct.unpack_from('>H', d, p); p += 2
        for _ in range(ncp):
            p += 2
            ty = d[p]; p += 1
            p += ids if ty == 2 else TYPE_SIZE[ty]
        nsf, = struct.unpack_from('>H', d, p); p += 2
        for _ in range(nsf):
            p += ids
            ty = d[p]; p += 1
            p += ids if ty == 2 else TYPE_SIZE[ty]
        nif, = struct.unpack_from('>H', d, p); p += 2
        fields = []
        for _ in range(nif):
            sid = self.rid(p); p += ids
            ty = d[p]; p += 1
            fields.append((self.strings.get(sid, '?'), ty))
        self.cls[cid] = (sup, fields)
        return p

    def read_ref_field(self, cid, off, want):
        """Walk instance data (own class first, then supers) to find field `want`."""
        d, ids = self.d, self.idsize
        c = cid
        while c:
            sup, fields = self.cls.get(c, (0, []))
            for fname, ty in fields:
                sz = ids if ty == 2 else TYPE_SIZE[ty]
                if fname == want and ty == 2:
                    return self.rid(off)
                off += sz
            c = sup
        return None


h = H(sys.argv[1])
name_by_cid = {cid: h.cname(cid) for cid in set(h.obj_cls.values()) if cid}
finref_cids = [cid for cid, nm in name_by_cid.items() if nm == 'java.lang.ref.FinalizerReference']
print('FinalizerReference class ids:', [hex(c) for c in finref_cids])

referents = collections.Counter()
total = 0
for oid, cid, off, nb in h.instances:
    if cid not in finref_cids:
        continue
    total += 1
    r = h.read_ref_field(cid, off, 'referent')
    if not r:
        referents['<null/cleared>'] += 1
        continue
    rc = h.obj_cls.get(r)
    referents[h.cname(rc) if rc else '<primitive array>'] += 1

print('total FinalizerReference instances: %d' % total)
print('\n=== what is awaiting finalization ===')
for nm, c in referents.most_common(15):
    print('%8d  %s' % (c, nm))
