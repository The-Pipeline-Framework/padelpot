#!/usr/bin/env python3
"""Check reference and correction across three roots on a fresh real-model server."""

import argparse
import json
import time
import urllib.request
import uuid


def request(base, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    call = urllib.request.Request(
        base + path, data=data, headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(call, timeout=5) as response:
        return json.load(response)


def await_owner_response(base, owner_id, execution_id):
    for _ in range(600):
        execution = request(base, "/api/executions/" + execution_id)
        if execution["status"] == "SUCCEEDED":
            owner = request(base, "/api/owners/" + owner_id)
            replies = [turn["text"] for turn in owner["messages"] if turn["role"] == "agent"]
            if replies:
                return replies[-1]
            raise AssertionError(f"Execution {execution_id} completed without an accepted reply")
        if execution["status"] in ("FAILED", "CANCELLED", "WAITING_EXTERNAL"):
            raise AssertionError(
                f"Execution {execution_id} did not produce a natural reply: {execution['status']}"
            )
        time.sleep(0.3)
    raise AssertionError(f"Execution {execution_id} did not complete")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, help="Fresh real-model PadelPot server URL")
    args = parser.parse_args()
    base = args.base.rstrip("/")
    owner_id = "alba"
    statements = (
        "My favourite warm-up is stretching.",
        "Correction: I prefer jogging because it helps me focus.",
        "Which warm-up did I settle on, and why?",
    )
    responses = []
    for statement in statements:
        accepted = request(
            base,
            "/api/messages",
            {"ownerId": owner_id, "messageId": uuid.uuid4().hex, "text": statement},
        )
        responses.append(await_owner_response(base, owner_id, accepted["executionId"]))
    answer = responses[-1].lower()
    if "jogg" not in answer or "focus" not in answer:
        raise AssertionError(f"The latest correction and its reason were not both recalled: {responses[-1]}")
    print("Answer:", responses[-1])
    print("PASS: real model recalled the latest owner correction and its reason")


if __name__ == "__main__":
    main()
