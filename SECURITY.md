# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 1.0.x   | :white_check_mark: |

## Reporting a Vulnerability

If you discover a security vulnerability in StackSage, please report it responsibly.

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, please email: **[msupresh31@gmail.com](mailto:msupresh31@gmail.com)**

Include the following in your report:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

You can expect an initial response within **72 hours**. Once confirmed, a fix will be prioritized and released as a patch version.

## Security Measures

StackSage implements the following security practices:

- **Input validation** — File uploads are validated for type, size, extension, and binary content detection
- **Path traversal protection** — All file paths are normalized and validated against the upload directory boundary
- **Rate limiting** — Token-bucket rate limiting on API endpoints to prevent abuse
- **Credential encryption** — Sensitive properties encrypted via Jasypt; secrets managed through environment variables
- **Non-root Docker image** — Production container runs as a dedicated unprivileged user
- **Dependency scanning** — GitHub CodeQL enabled for static analysis
- **Minimal permissions** — CI workflows use least-privilege `GITHUB_TOKEN` scopes
