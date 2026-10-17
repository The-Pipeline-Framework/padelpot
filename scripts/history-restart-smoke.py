#!/usr/bin/env python3
"""Exercise saved conversation turns across a real fixture-process restart."""

import json
import socket
import subprocess
import tempfile
import time
import urllib.request
import uuid
from pathlib import Path


PROJECT = Path(__file__).resolve().parents[1]
JAR = PROJECT / "app/target/quarkus-app/quarkus-run.jar"


def free_port():
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def request(base, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    call = urllib.request.Request(
        base + path, data=data, headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(call, timeout=2) as response:
        return json.load(response)


def launch(arguments, base, log_path):
    log = log_path.open("ab")
    process = subprocess.Popen(arguments, cwd=PROJECT, stdout=log, stderr=subprocess.STDOUT)
    for _ in range(150):
        if process.poll() is not None:
            log.close()
            raise RuntimeError("server exited: " + log_path.read_text()[-3000:])
        try:
            request(base, "/api/view")
            return process, log
        except Exception:
            time.sleep(0.2)
    stop(process, log)
    raise RuntimeError("server did not become ready: " + log_path.read_text()[-3000:])


def stop(process, log):
    process.terminate()
    try:
        process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=10)
    log.close()


def await_prompt(base, owner_id, execution_id):
    for _ in range(150):
        owner = request(base, "/api/owners/" + owner_id)
        for pending in owner["pending"]:
            if pending["executionId"] == execution_id:
                return pending["prompt"]
        time.sleep(0.2)
    raise AssertionError("execution did not reach the owner Await boundary")


def main():
    if not JAR.exists():
        raise SystemExit("Build the fixture first: ./mvnw -q -Pfixture -DskipTests package")
    port = free_port()
    base = f"http://127.0.0.1:{port}"
    with tempfile.TemporaryDirectory(prefix="padelpot-history-restart-") as directory:
        root = Path(directory)
        log_path = root / "server.log"
        arguments = [
            "java",
            f"-Dquarkus.http.port={port}",
            "-Dquarkus.grpc.server.port=0",
            "-Dquarkus.datasource.jdbc.url=jdbc:h2:file:"
            + str(root / "history")
            + ";DB_CLOSE_ON_EXIT=FALSE",
            "-Dpipeline.telemetry.replay.enabled=false",
            "-jar",
            str(JAR),
        ]
        first, first_log = launch(arguments, base, log_path)
        try:
            marker = "restart-proof-" + uuid.uuid4().hex[:10]
            accepted = request(
                base,
                "/api/messages",
                {"ownerId": "alba", "messageId": marker, "text": "I enjoy quiet courts"},
            )
            await_prompt(base, "alba", accepted["executionId"])
        finally:
            stop(first, first_log)

        second, second_log = launch(arguments, base, log_path)
        try:
            owner = request(base, "/api/owners/alba")
            assert "I enjoy quiet courts" in [message["text"] for message in owner["messages"]]
            assert not owner["pending"], "Await state should remain process-local"
            follow_up = request(
                base,
                "/api/messages",
                {
                    "ownerId": "alba",
                    "messageId": uuid.uuid4().hex,
                    "text": "What did I say before?",
                },
            )
            prompt = await_prompt(base, "alba", follow_up["executionId"])
            assert "quiet courts" in prompt, prompt
        finally:
            stop(second, second_log)
    print("PASS: saved turn and scoped follow-up survive a real process restart")


if __name__ == "__main__":
    main()
