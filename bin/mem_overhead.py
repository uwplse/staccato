import sys
import yaml

def parse_memory(mem_files):
    total = 0
    n = 0
    for mem_file in mem_files:
        with open(mem_file, 'r') as f:
            for l in f:
                n += 1
                total += long(l)
    return (total / float(n))

project = sys.argv[2]
doc = yaml.load(open(sys.argv[1]))
base_mem_f = doc["memory"][project]["base"]
inst_mem_f = doc["memory"][project]["inst"]
base_mem = parse_memory(base_mem_f)
inst_mem = parse_memory(inst_mem_f)

overhead = ((inst_mem - base_mem) / float(base_mem)) * 100

print "\def\%smem{%.02f}" % (project, overhead)
