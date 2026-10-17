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
        owner = request(base, "/api/owners/" + owner_id)
        for pending in owner["pending"]:
            if pending["executionId"] == execution_id:
                return pending["prompt"]
        execution = request(base, "/api/executions/" + execution_id)
        if execution["status"] in ("FAILED", "SUCCEEDED", "CANCELLED"):
            raise AssertionError(
                f"Execution {execution_id} ended before an owner response: {execution['status']}"
            )
        time.sleep(0.3)
    raise AssertionError(f"Execution {execution_id} did not reach owner Await")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, help="Fresh real-model PadelPot server URL")
    args = parser.parse_args()
    base = args.base.rstrip("/")
    owner_id = "alba"
    statements = (
        "I was considering Thursday at 19:00 for a game.",
        "Correction: Thursday at 20:00 works because I finish work at 19:30.",
        "What time did I settle on for Thursday, and why?",
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
    if "20" not in answer or ("work" not in answer and "19:30" not in answer):
        raise AssertionError(f"The latest correction and its reason were not both recalled: {responses[-1]}")
    print("Answer:", responses[-1])
    print("PASS: real model recalled the latest owner correction and its reason")


if __name__ == "__main__":
    main()
