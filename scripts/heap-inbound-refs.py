import sys, collections, struct, pathlib

# Reuse the hprof parser from the sibling script (resolved next to this file).
_sib = pathlib.Path(__file__).resolve().parent / "heap-finalizer-queue.py"
src = _sib.read_text().split("h = H(")[0]
ns = {}
exec(compile(src, str(_sib), "exec"), ns)
H = ns["H"]

h = H(sys.argv[1])
target_name = sys.argv[2] if len(sys.argv) > 2 else 'java.util.regex.Matcher'

name_of_cid = {}
for cid in set(h.obj_cls.values()):
    if cid:
        name_of_cid[cid] = h.cname(cid)

target_cids = {cid for cid, nm in name_of_cid.items() if nm == target_name}
targets = {oid for oid, cid, _, _ in h.instances if cid in target_cids}
print('target objects (%s): %d' % (target_name, len(targets)))

ids = h.idsize
inbound = collections.Counter()

# instance field edges
for oid, cid, off, nb in h.instances:
    c = cid
    o = off
    while c:
        sup, fields = h.cls.get(c, (0, []))
        for fname, ty in fields:
            if ty == 2:
                v = h.rid(o)
                if v in targets:
                    inbound['%s.%s' % (h.cname(cid), fname)] += 1
                o += ids
            else:
                o += ns["TYPE_SIZE"][ty]
        c = sup

# object array edges — re-scan heap segments for 0x22
d = h.d
p = d.index(b'\0') + 1 + 4 + 8
n = len(d)
arr_hits = collections.Counter()
while p + 9 <= n:
    tag = d[p]
    ln, = struct.unpack_from('>I', d, p + 5)
    p += 9
    end = p + ln
    if tag in (0x0C, 0x1C):
        q = p
        while q < end:
            t = d[q]; q += 1
            if t == 0xFF: q += ids
            elif t == 0x01: q += ids * 2
            elif t == 0x08: q += ids + 8
            elif t in (0x02, 0x03): q += ids + 8
            elif t in (0x04, 0x06): q += ids + 4
            elif t in (0x05, 0x07, 0xC3, 0x89, 0x8A, 0x8B, 0x8D): q += ids
            elif t == 0x8E: q += ids + 8
            elif t == 0x20: q = h.class_dump(q)
            elif t == 0x21:
                q += ids + 4 + ids
                nb, = struct.unpack_from('>I', d, q); q += 4 + nb
            elif t == 0x22:
                q += ids + 4
                ne, = struct.unpack_from('>I', d, q); q += 4
                acid = h.rid(q); q += ids
                for i in range(ne):
                    v = h.rid(q + i * ids)
                    if v in targets:
                        arr_hits[h.cname(acid)] += 1
                q += ne * ids
            elif t == 0x23:
                q += ids + 4
                ne, = struct.unpack_from('>I', d, q); q += 4
                et = d[q]; q += 1
                q += ne * ns["TYPE_SIZE"].get(et, 1)
            else:
                raise ValueError('subtag 0x%x' % t)
    p = end

print('\n=== inbound refs from instance fields ===')
for k, c in inbound.most_common(20):
    print('%8d  %s' % (c, k))
print('\n=== inbound refs from object arrays ===')
for k, c in arr_hits.most_common(20):
    print('%8d  %s' % (c, k))
tot = sum(inbound.values()) + sum(arr_hits.values())
print('\ntotal inbound edges: %d  (targets: %d)' % (tot, len(targets)))
