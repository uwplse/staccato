import sys
import subprocess

def lint_source_tree(src):
    with open('/dev/null', 'w') as f:
        subprocess.check_call(["git", "diff", "--exit-code"], stdout = f, stderr = subprocess.STDOUT, cwd = src)
        subprocess.check_call(["git", "diff", "--cached", "--exit-code"], stdout = f, stderr = subprocess.STDOUT, cwd = src)
        subprocess.check_call(["git", "diff", "--exit-code", "master", "line_counts"], stdout = f, stderr = subprocess.STDOUT, cwd = src)

for i in sys.argv[1:]:
    lint_source_tree(i)

sys.exit(0)
