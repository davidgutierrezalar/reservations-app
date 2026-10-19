---
name: legal-review
description: Review the app for legal and compliance issues before publishing or deploying it - accessibility (WCAG, EU Accessibility Act, Colombian rules), disclosure of AI use, personal data and privacy (Colombian Ley 1581 / habeas data, GDPR), cookies and third-party requests, licenses, trademarks and terms. Use when the user asks for a legal, compliance, privacy, accessibility or "is this OK to publish" review, or before a deploy or a new public feature.
---

# Legal and compliance review

Produce a findings report for this repository. **Do not change code** unless the user asks; this skill only finds and explains.

This is not legal advice. Say so once at the top of the report, and recommend a lawyer for anything that ships commercially or handles real users' data.

## Before you start

1. Ask, or infer from the README, what the app is being used for:
   - portfolio demo with no real users;
   - a public service with real users;
   - a commercial product.
   The audience changes which findings matter. Also ask where the users are: Colombia, the EU, the US. Default: a portfolio demo, published from Colombia, viewable worldwide.
2. Laws in this area move fast, especially on AI. For anything marked *verify current status* in [checklist.md](checklist.md), check the current state with a web search before reporting it. Cite the source and its date.

## What to inspect

Work through every area in [checklist.md](checklist.md). For each one, look at the actual code, not just the README. These are the places in this repo where the issues live:

| Area | Where to look |
|---|---|
| Accessibility | `frontend/src/**/*.tsx` (labels, roles, alt text, keyboard), `frontend/src/index.css` (contrast, focus styles, `prefers-reduced-motion`) |
| AI disclosure | `README.md`, landing page `frontend/src/pages/LandingPage.tsx`, footer, git history (`git log --format=%B \| grep -i co-authored`) |
| Personal data | `V1__init.sql`/`V2__*.sql` (what is stored), `AuthDtos.RegisterRequest`, `audit_log` (stores actor and details), `LoginPage.tsx` (consent at sign-up) |
| Cookies and third parties | `frontend/index.html` (external fonts and scripts), `api.ts` (`localStorage`), any analytics |
| Licenses | root `LICENSE` (missing = all rights reserved), `backend/pom.xml`, `frontend/package.json`, fonts, images and illustrations |
| Branding | app name "Slotline", logos, sample copy that names real places or companies |
| Emails | `EmailPreviewPage.tsx`: if emails are ever sent, check sender identity and unsubscribe rules |
| Security as a legal duty | password hashing, secrets handling, `docker-compose.yml` demo credentials |

Use Grep and Read to collect evidence. Every finding needs a `file:line` reference or an explicit "missing: no X found".

## Report format

Write the report in the user's language. Use this structure:

1. **Summary:** three or four lines. State the scenario assumed, and give the count of blocking, important and optional findings.
2. **Findings:** a table sorted by severity, with these columns:
   - **Severity:** *Blocking* means illegal or high-risk for the assumed scenario. *Important* means expected by law or by reviewers, so fix it before going public. *Optional* means good practice.
   - **Area**
   - **Rule:** the law or standard, with its article or criterion.
   - **Evidence:** `file:line`.
   - **What to do:** one concrete fix.
3. **Documents the app needs:** for example a privacy policy, data processing authorization, terms of use, accessibility statement or AI notice. For each, say whether it exists, and where it should be linked (footer, register form).
4. **Verified on:** which legal points you checked online, with source links and dates, and which ones you could not verify.

Keep each finding to one or two sentences. Do not pad the report with areas that do not apply; list them in one line as "not applicable" with the reason.
