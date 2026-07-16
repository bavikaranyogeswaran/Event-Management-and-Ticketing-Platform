# ADR-0004 — Cloudinary over S3-compatible object storage

**Status:** Accepted · 2026-07-16

## Context
The source plan called for generic S3-compatible storage. The user chose Cloudinary (project constraint). The platform's file needs are image-centric: event banners, profile images, plus private CSV exports. Images need resizing/compression to hit the < 500 KB delivery budget — with S3 that would mean building an image-processing job pipeline.

## Decision
Use Cloudinary behind the `ObjectStorage` port. Browser uploads go **directly to Cloudinary** using backend-generated signed upload parameters (random `public_id`, never the original filename); the backend verifies each asset via the Admin API before attaching it. Public images are delivered through Cloudinary's CDN with on-the-fly transformations (auto format/quality). Private assets (CSV exports) are stored as `authenticated` raw assets, downloadable only via short-lived signed URLs.

## Consequences
- ✅ Image resizing, compression, and CDN delivery come free — the planned image-processing jobs are deleted from scope.
- ✅ Upload bytes never pass through the backend (bandwidth + thread savings).
- ✅ Free tier comfortably covers MVP volumes.
- ⚠️ Vendor lock-in is limited to the `CloudinaryObjectStorage` adapter; the port keeps a swap to S3/MinIO contained (risk R-11).
- ⚠️ Signed-upload and Admin-API verification flows are Cloudinary-specific and need integration-testing against the real sandbox account.
