from dataclasses import dataclass

import boto3
from botocore.config import Config

from .config import Settings


@dataclass(frozen=True)
class PresignedRequest:
    url: str
    headers: dict[str, str]


class ObjectStorage:
    def __init__(self, settings: Settings):
        if not all((settings.s3_endpoint, settings.s3_bucket, settings.s3_access_key, settings.s3_secret_key)):
            raise RuntimeError("Object storage is not configured")
        self.bucket = settings.s3_bucket
        self.client = boto3.client(
            "s3",
            endpoint_url=settings.s3_endpoint,
            region_name=settings.s3_region,
            aws_access_key_id=settings.s3_access_key,
            aws_secret_access_key=settings.s3_secret_key,
            config=Config(signature_version="s3v4"),
        )

    def upload(self, key: str, size: int, sha256: str) -> PresignedRequest:
        headers = {
            "Content-Type": "application/octet-stream",
            "x-amz-meta-sha256": sha256,
        }
        url = self.client.generate_presigned_url(
            "put_object",
            Params={
                "Bucket": self.bucket,
                "Key": key,
                "ContentType": "application/octet-stream",
                "Metadata": {"sha256": sha256},
                "ContentLength": size,
            },
            ExpiresIn=600,
        )
        return PresignedRequest(url, headers)

    def download(self, key: str) -> str:
        return self.client.generate_presigned_url(
            "get_object",
            Params={"Bucket": self.bucket, "Key": key},
            ExpiresIn=300,
        )

    def head(self, key: str) -> dict:
        return self.client.head_object(Bucket=self.bucket, Key=key)
