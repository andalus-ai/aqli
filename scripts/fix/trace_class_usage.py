#!/usr/bin/env python3
import os
import json
import subprocess
import time
from pathlib import Path

# Config
SEARCH_DIR = "."
STATE_FILE = "class_usage_state.json"
REPORT_FILE = "dead_code_report.md"

def load_state():
    if os.path.exists(STATE_FILE):
        with open(STATE_FILE, 'r') as f:
            return json.load(f)
    return {}

def save_state(state):
    with open(STATE_FILE, 'w') as f:
        json.dump(state, f, indent=2)

def generate_report(state):
    unused = []
    used = []
    
    for path, data in state.items():
        if data.get('used_count', 0) == 0:
            unused.append(path)
        else:
            used.append(path)
            
    with open(REPORT_FILE, 'w') as f:
        f.write("# Class Usage Report\n\n")
        f.write(f"Total files scanned: {len(state)}\n")
        f.write(f"Potentially Unused (Dead Code): {len(unused)}\n")
        f.write(f"Actively Used: {len(used)}\n\n")
        
        f.write("## 🗑️ Potentially Unused Files\n")
        for path in sorted(unused):
            f.write(f"- `{path}`\n")

    print(f"\n[+] Report generated at {REPORT_FILE}")

def main():
    print("Gathering .java files...")
    all_java_files = list(Path(SEARCH_DIR).rglob("*.java"))
    state = load_state()
    
    # We will persist the modified time to avoid rescanning unchanged files
    updated_count = 0
    
    try:
        for idx, filepath in enumerate(all_java_files):
            path_str = str(filepath)
            
            # Exclude some directories like target/ generated code
            if "/target/generated-sources/" in path_str:
                continue
                
            mtime = os.path.getmtime(path_str)
            classname = filepath.stem
            
            # Check if we already scanned this exact file version
            if path_str in state and state[path_str].get('mtime') == mtime:
                continue
            
            print(f"[{idx+1}/{len(all_java_files)}] Scanning usage for: {classname} ... ", end="", flush=True)
            
            # Use ripgrep (rg) to find exact word matches of the classname
            # Excluding the file itself to see if it's referenced ANYWHERE else.
            cmd = ["rg", "-w", classname, "-t", "java", "-g", f"!{path_str}"]
            result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
            
            # Count how many files reference this class
            # (rg outputs matched lines, we count non-empty lines)
            match_lines = [line for line in result.stdout.split('\n') if line.strip()]
            used_count = len(match_lines)
            
            state[path_str] = {
                'classname': classname,
                'mtime': mtime,
                'used_count': used_count,
                'last_scanned': time.time()
            }
            
            print(f"Found {used_count} references.")
            updated_count += 1
            
            # Save state periodically to not lose progress
            if updated_count % 10 == 0:
                save_state(state)
                
    except KeyboardInterrupt:
        print("\n[!] Scan interrupted by user. Saving current state...")
    finally:
        save_state(state)
        generate_report(state)

if __name__ == "__main__":
    main()
