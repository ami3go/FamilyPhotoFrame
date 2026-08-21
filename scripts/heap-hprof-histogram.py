import struct, sys, collections

TYPE_SIZE = {2: None, 4: 1, 5: 2, 6: 4, 7: 8, 8: 1, 9: 2, 10: 4, 11: 8}
TYPE_NAME = {4: 'boolean', 5: 'char', 6: 'float', 7: 'double',
             8: 'byte', 9: 'short', 10: 'int', 11: 'long', 2: 'object'}


class Hprof:
    def __init__(self, path):
        self.d = open(path, 'rb').read()
        self.strings = {}
        self.class_name_id = {}    # class obj id -> name string id
        self.counts = collections.Counter()
        self.bytes = collections.Counter()
        self.arr_counts = collections.Counter()
        self.arr_bytes = collections.Counter()
        self.parse()

    def cname(self, cid):
        sid = self.class_name_id.get(cid)
        n = self.strings.get(sid, None)
        return n if n else 'unknown@0x%x' % cid

    def parse(self):
        d = self.d
        p = d.index(b'\0') + 1
        self.idsize, = struct.unpack_from('>I', d, p); p += 4
        p += 8
        n = len(d)
        while p < n:
            if p + 9 > n:
                break
            tag = d[p]
            length, = struct.unpack_from('>I', d, p + 5)
            p += 9
            end = p + length
            if tag == 0x01:      # STRING
                sid = self.rid(p)
                self.strings[sid] = d[p + self.idsize:end].decode('utf-8', 'replace')
            elif tag == 0x02:    # LOAD_CLASS
                q = p + 4
                cid = self.rid(q); q += self.idsize + 4
                self.class_name_id[cid] = self.rid(q)
            elif tag in (0x0C, 0x1C):
                self.heap(p, end)
            p = end

    def rid(self, p):
        if self.idsize == 4:
            return struct.unpack_from('>I', self.d, p)[0]
        return struct.unpack_from('>Q', self.d, p)[0]

    def heap(self, p, end):
        d, ids = self.d, self.idsize
        while p < end:
            t = d[p]; p += 1
            if t == 0xFF:
                p += ids
            elif t in (0x01, 0x08):
                p += ids + 8 if t == 0x08 else ids + ids
            elif t == 0x02:
                p += ids + 8
            elif t == 0x03:
                p += ids + 8
            elif t == 0x04 or t == 0x06:
                p += ids + 4
            elif t in (0x05, 0x07, 0xC3):
                p += ids
            elif t in (0x89, 0x8A, 0x8B, 0x8D):
                p += ids
            elif t == 0x8E:
                p += ids + 8
            elif t == 0x20:      # CLASS_DUMP
                p = self.class_dump(p)
            elif t == 0x21:      # INSTANCE_DUMP
                oid = self.rid(p); p += ids + 4
                cid = self.rid(p); p += ids
                nb, = struct.unpack_from('>I', d, p); p += 4
                nm = self.cname(cid)
                self.counts[nm] += 1
                self.bytes[nm] += nb + 16
                p += nb
            elif t == 0x22:      # OBJECT_ARRAY
                p += ids + 4
                ne, = struct.unpack_from('>I', d, p); p += 4
                cid = self.rid(p); p += ids
                nm = self.cname(cid)
                self.arr_counts[nm] += 1
                self.arr_bytes[nm] += ne * ids + 16
                p += ne * ids
            elif t == 0x23:      # PRIMITIVE_ARRAY
                p += ids + 4
                ne, = struct.unpack_from('>I', d, p); p += 4
                et = d[p]; p += 1
                sz = TYPE_SIZE.get(et, 1)
                nm = TYPE_NAME.get(et, '?') + '[]'
                self.arr_counts[nm] += 1
                self.arr_bytes[nm] += ne * sz + 16
                p += ne * sz
            else:
                raise ValueError('unknown subtag 0x%x at %d' % (t, p - 1))
        return p

    def class_dump(self, p):
        d, ids = self.d, self.idsize
        p += ids + 4 + ids * 6 + 4
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
        p += nif * (ids + 1)
        return p


h = Hprof(sys.argv[1])
total = sum(h.bytes.values()) + sum(h.arr_bytes.values())
print('total shallow: %.1f MB   instances: %d' % (total / 1048576, sum(h.counts.values())))
print('\n=== top by shallow bytes (instances) ===')
for nm, b in h.bytes.most_common(25):
    print('%10.2f MB %8d  %s' % (b / 1048576, h.counts[nm], nm))
print('\n=== top by shallow bytes (arrays) ===')
for nm, b in h.arr_bytes.most_common(20):
    print('%10.2f MB %8d  %s' % (b / 1048576, h.arr_counts[nm], nm))
print('\n=== top app classes by count ===')
for nm, c in h.counts.most_common(400):
    if 'familyphotoframe' in nm:
        print('%8d  %8.2f MB  %s' % (c, h.bytes[nm] / 1048576, nm))
