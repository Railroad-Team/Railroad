#!/usr/bin/env python3
"""
Pick a random partially completed or pending inspection todo from inspections-todo.md.

Usage: python scripts/pick_todo.py [starter|easy|medium|hard]
"""

from __future__ import annotations

import argparse
import random
import re
import sys
from pathlib import Path


TODO_FILE = Path(__file__).resolve().parents[1] / "inspections-todo.md"
DIFFICULTIES = {"starter", "easy", "medium", "hard"}
STATUS_CHAR_TO_MEANING = {" ": "open", "-": "partial"}


def load_todos() -> list[dict[str, str]]:
    todos: list[dict[str, str]] = []
    pattern = re.compile(r"\s*-\s*\[([ x\-])\]\s*(.*)")
    difficulty_pattern = re.compile(r"\[(starter|easy|medium|hard)\]", re.IGNORECASE)

    lines = TODO_FILE.read_text().splitlines()
    i = 0
    while i < len(lines):
        match = pattern.match(lines[i])
        if not match:
            i += 1
            continue

        status = match.group(1)
        if status not in STATUS_CHAR_TO_MEANING:
            i += 1
            continue

        tail = match.group(2).strip()
        difficulty_match = difficulty_pattern.search(lines[i])
        difficulty = difficulty_match.group(1).lower() if difficulty_match else None

        text_lines = [tail]
        j = i + 1
        while j < len(lines):
            next_line = lines[j]
            if pattern.match(next_line) or not next_line.startswith("  "):
                break
            text_lines.append(next_line.strip())
            j += 1

        todos.append({
            "line": str(i + 1),
            "status": STATUS_CHAR_TO_MEANING[status],
            "difficulty": difficulty,
            "text": " ".join(t for t in text_lines if t),
        })

        i = j
    return todos


def choose_todo(todos: list[dict[str, str]], difficulty: str | None) -> dict[str, str] | None:
    filtered = [
        todo for todo in todos
        if (difficulty is None or todo["difficulty"] == difficulty.lower())
    ]

    if not filtered:
        return None

    return random.choice(filtered)


def main() -> None:
    parser = argparse.ArgumentParser(description="Pick a random inspection todo.")
    parser.add_argument(
        "difficulty",
        nargs="?",
        choices=sorted(DIFFICULTIES),
        help="Optional difficulty filter to narrow the selection.",
    )

    args = parser.parse_args()
    todos = load_todos()
    entry = choose_todo(todos, args.difficulty)
    if entry is None:
        difficulty_label = args.difficulty or "any"
        print(f"No matching todos found for difficulty {difficulty_label}.")
        sys.exit(1)

    difficulty_display = entry["difficulty"] or "unmarked"
    print(f"Line {entry['line']} [{difficulty_display}, {entry['status']}]: {entry['text']}")


if __name__ == "__main__":
    main()
