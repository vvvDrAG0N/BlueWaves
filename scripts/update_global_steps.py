import sys
import os

def update_steps(step, action, outcome, phase):
    file_path = os.path.join("docs", "agent_memory", "GLOBAL_STEPS.md")
    if not os.path.exists(file_path):
        print(f"Error: {file_path} not found.")
        return

    new_line = f"| {step} | {action} | {outcome} | {phase} |\n"
    
    with open(file_path, "a", encoding="utf-8") as f:
        f.write(new_line)
    
    print(f"Step {step} recorded successfully.")

if __name__ == "__main__":
    if len(sys.argv) < 5:
        print("Usage: python update_global_steps.py <step> <action> <outcome> <phase>")
    else:
        update_steps(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4])
