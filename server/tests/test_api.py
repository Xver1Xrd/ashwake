import base64
import hashlib
import os
from pathlib import Path

os.environ["ASHWAKE_ENVIRONMENT"] = "test"
os.environ["ASHWAKE_DATABASE_URL"] = "sqlite+aiosqlite:///./test-ashwake.db"
os.environ["ASHWAKE_JWT_SECRET"] = "test-secret-that-is-longer-than-thirty-two-bytes"

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi.testclient import TestClient

from app.main import app


def b64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode()


def identity(username: str) -> tuple[ec.EllipticCurvePrivateKey, dict]:
    key = ec.generate_private_key(ec.SECP256R1())
    public = key.public_key().public_bytes(
        serialization.Encoding.PEM,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    ).decode()
    return key, {
        "username": username,
        "display_name": username.title(),
        "device_name": f"{username} phone",
        "public_key_pem": public,
        "encryption_public_key_pem": public,
    }


def auth(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def register(client: TestClient, username: str):
    key, body = identity(username)
    response = client.post("/v1/auth/register", json=body)
    assert response.status_code == 201, response.text
    return key, response.json()


def test_social_partnership_and_encrypted_sync():
    Path("test-ashwake.db").unlink(missing_ok=True)
    with TestClient(app) as client:
        alice_key, alice = register(client, "alice")
        _, bob = register(client, "bob_user")

        recovery_token = b64url(os.urandom(32))
        configured = client.put(
            "/v1/auth/recovery",
            json={
                "token_hash": hashlib.sha256(recovery_token.encode()).hexdigest(),
                "encrypted_bundle": "encrypted-recovery-bundle-long-enough-for-validation",
            },
            headers=auth(alice["access_token"]),
        )
        assert configured.status_code == 204
        _, recovered_device = identity("unused")
        recovered = client.post(
            "/v1/auth/recover",
            json={
                "username": "alice",
                "recovery_token": recovery_token,
                "device_name": "replacement phone",
                "public_key_pem": recovered_device["public_key_pem"],
                "encryption_public_key_pem": recovered_device["encryption_public_key_pem"],
            },
        )
        assert recovered.status_code == 200, recovered.text
        assert recovered.json()["encrypted_bundle"].startswith("encrypted-recovery")
        # Код восстановления одноразовый: повторное использование означает,
        # что его могли скопировать с потерянного устройства.
        repeated_recovery = client.post(
            "/v1/auth/recover",
            json={
                "username": "alice",
                "recovery_token": recovery_token,
                "device_name": "stolen phone",
                "public_key_pem": recovered_device["public_key_pem"],
                "encryption_public_key_pem": recovered_device["encryption_public_key_pem"],
            },
        )
        assert repeated_recovery.status_code == 401
        recovered_session = recovered.json()["session"]
        assert client.post(
            "/v1/auth/logout",
            json={"refresh_token": recovered_session["refresh_token"]},
        ).status_code == 204
        assert client.post(
            "/v1/auth/refresh",
            json={"refresh_token": recovered_session["refresh_token"]},
        ).status_code == 401

        duplicate = client.post("/v1/auth/register", json=identity("alice")[1])
        assert duplicate.status_code == 409

        hidden = client.patch(
            "/v1/profile",
            json={"bio": "private profile", "discoverable": False},
            headers=auth(bob["access_token"]),
        )
        assert hidden.status_code == 200
        assert client.get("/v1/profiles/@bob_user", headers=auth(alice["access_token"])).status_code == 404
        visible = client.patch(
            "/v1/profile",
            json={"display_name": "Bob", "discoverable": True},
            headers=auth(bob["access_token"]),
        )
        assert visible.status_code == 200

        found = client.get("/v1/profiles/@bob_user", headers=auth(alice["access_token"]))
        assert found.status_code == 200
        bob_id = found.json()["id"]

        request = client.post(
            "/v1/friends/requests",
            json={"username": "bob_user"},
            headers=auth(alice["access_token"]),
        )
        assert request.status_code == 201, request.text
        accepted = client.post(
            f"/v1/friends/requests/{request.json()['id']}/accept",
            headers=auth(bob["access_token"]),
        )
        assert accepted.status_code == 200
        assert len(client.get("/v1/friends", headers=auth(alice["access_token"])).json()) == 1

        partnership = client.post(
            "/v1/partnerships",
            json={"username": "bob_user"},
            headers=auth(alice["access_token"]),
        )
        assert partnership.status_code == 201
        active = client.post(
            f"/v1/partnerships/{partnership.json()['id']}/accept",
            headers=auth(bob["access_token"]),
        )
        assert active.status_code == 200, active.text
        couple_space = active.json()["space_id"]

        # Участник с ролью editor не может подменить конверты ключей пространства.
        editor_envelope = client.put(
            f"/v1/spaces/{couple_space}/envelopes",
            json={"device_id": bob["device_id"], "key_version": 1, "envelope": "x" * 32},
            headers=auth(bob["access_token"]),
        )
        assert editor_envelope.status_code == 403
        owner_envelope = client.put(
            f"/v1/spaces/{couple_space}/envelopes",
            json={"device_id": bob["device_id"], "key_version": 1, "envelope": "x" * 32},
            headers=auth(alice["access_token"]),
        )
        assert owner_envelope.status_code == 204

        mutation = {
            "mutations": [{
                "space_id": couple_space,
                "record_id": "32f4ccf4-7220-49dc-9995-8b9ed315c634",
                "record_type": "task",
                "key_version": 1,
                "base_revision": 0,
                "ciphertext": "encrypted-task-payload",
                "deleted": False,
            }]
        }
        pushed = client.post("/v1/sync/push", json=mutation, headers=auth(alice["access_token"]))
        assert pushed.status_code == 200, pushed.text
        assert pushed.json()[0] == {
            "record_id": mutation["mutations"][0]["record_id"],
            "revision": 1,
            "conflict_revision": None,
            "accepted": True,
        }

        pulled = client.get("/v1/sync/pull", headers=auth(bob["access_token"]))
        assert pulled.status_code == 200
        assert pulled.json()["records"][0]["ciphertext"] == "encrypted-task-payload"

        conflict = client.post("/v1/sync/push", json=mutation, headers=auth(bob["access_token"]))
        assert conflict.json()[0]["accepted"] is False
        assert conflict.json()[0]["conflict_revision"] == 1

        # Участник не получает доступ к личному пространству другого пользователя.
        alice_personal = next(space for space in client.get("/v1/spaces", headers=auth(alice["access_token"])).json() if space["type"] == "personal")
        forbidden = mutation.copy()
        forbidden["mutations"] = [dict(mutation["mutations"][0], space_id=alice_personal["id"], record_id="4fa175f1-8b6d-438a-9e80-f99d5695d426")]
        assert client.post("/v1/sync/push", json=forbidden, headers=auth(bob["access_token"])).status_code == 404

        # Вход по одноразовому challenge и подписи ключом устройства.
        challenge = client.post(
            "/v1/auth/challenge",
            json={"username": "alice", "device_id": alice["device_id"]},
        )
        value = challenge.json()["challenge"]
        signature = alice_key.sign(value.encode(), ec.ECDSA(hashes.SHA256()))
        verified = client.post(
            "/v1/auth/verify",
            json={
                "challenge_id": challenge.json()["challenge_id"],
                "challenge": value,
                "signature": b64url(signature),
            },
        )
        assert verified.status_code == 200, verified.text
        assert client.post(
            "/v1/auth/verify",
            json={
                "challenge_id": challenge.json()["challenge_id"],
                "challenge": value,
                "signature": b64url(signature),
            },
        ).status_code == 401

        refreshed = client.post("/v1/auth/refresh", json={"refresh_token": alice["refresh_token"]})
        assert refreshed.status_code == 200
        reused = client.post("/v1/auth/refresh", json={"refresh_token": alice["refresh_token"]})
        assert reused.status_code == 401

        ended = client.delete(
            f"/v1/partnerships/{partnership.json()['id']}",
            headers=auth(refreshed.json()["access_token"]),
        )
        assert ended.status_code == 204
        # История остаётся видимой обоим, но архивированное пространство больше
        # не принимает изменения.
        archived = dict(mutation["mutations"][0], base_revision=1)
        rejected = client.post(
            "/v1/sync/push",
            json={"mutations": [archived]},
            headers=auth(bob["access_token"]),
        )
        assert rejected.status_code == 409
