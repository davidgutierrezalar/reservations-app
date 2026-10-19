---
name: rewrite-prompt
description: Rewrite the user's request into a clear, complete prompt for Claude Code before doing the work - goal, context, scope, constraints, done criteria and open questions - then show it for approval. Use when the user invokes /rewrite-prompt, or says "mejora mi prompt", "reescribe mi prompt", "hazlo un prompt ideal", "optimiza esta petición" or similar.
---

# Rewrite the user's prompt

Turn a rough request into the prompt that would get the best result from Claude Code in this repo. Then let the user approve it before any work starts.

## Rules

- **Keep the intent exactly.** Do not add features, tasks or requirements the user did not ask for.
  - If something is missing and you have to assume it, write it under **Supuestos**.
  - If only the user can decide it, write it under **Preguntas**.
- **Ground it in this repo, not in guesses.** Read `CLAUDE.md` and look at the code the request touches before writing. Name real files, endpoints, components and skills, but only when you have checked that they exist.
- **Use the user's language** (Spanish by default). Keep code identifiers as they are.
- **Shorter is better** when it is equally clear. A request that is already clear and specific needs no full template; say so, and suggest at most one or two additions.
- **Do not start the work.** This skill ends when the rewritten prompt is shown. Run it only after the user approves, edits or says "ejecútalo".

## Steps

1. Read the request, and the earlier conversation if it refers to it ("lo de ayer", "ese botón").
2. Look up context:
   - `CLAUDE.md`;
   - the files and features the request names;
   - `git branch --show-current`;
   - which project skills apply: `verify-change`, `db-change`, `legal-review`.
3. Find what is missing. The usual gaps are:
   - **Why:** the goal behind the request, which decides trade-offs.
   - **Where:** which screen, endpoint, file or role.
   - **What "done" means:** tests, a check in the browser, a PR.
   - **Limits:** what not to touch, whether to commit or push, the branch.
   - **The deliverable:** code, a report, a document, an answer.
4. Write the prompt with the template below. Leave out any section that would be empty. Never pad a section to fill it.
5. Show it in a fenced `markdown` block so the user can copy it. Under the block, list what you changed, in up to three lines. Then ask one question: run it as is, adjust it, or answer the open questions first.

## Template

```markdown
## Objetivo
<Qué se quiere lograr y para qué, en 1-2 frases.>

## Contexto
<Estado actual relevante: archivos, pantallas o endpoints involucrados, decisiones previas que aplican.>

## Alcance
- Incluye: <...>
- No incluye: <...>

## Restricciones
<Rama y flujo de PR, sin commit si no se pide, compatibilidad con clientes existentes, estilo visual existente, idioma de la UI, etc.>

## Criterios de terminado
- <Comprobables: tests que deben pasar, qué ver en el navegador, skill a usar para verificar.>

## Entrega
<Qué espera recibir el usuario y en qué forma.>

## Supuestos
- <Lo que asumiste para completar el prompt; el usuario puede corregirlo.>

## Preguntas
- <Solo decisiones que únicamente el usuario puede tomar.>
```

## Example

Request: *"arregla lo del calendario que se ve feo en el celular"*

```markdown
## Objetivo
Que la vista de disponibilidad se vea y se use bien en pantallas de móvil.

## Contexto
La vista está en `frontend/src/Availability.tsx`, con estilos `.avail-*` y `.slot` en `frontend/src/index.css`. Se usa al reservar y al editar (`ReservationForm.tsx`).

## Alcance
- Incluye: la tira de días y la grilla de bloques de 30 min a 375 px de ancho.
- No incluye: cambios en el backend ni en la lógica de disponibilidad.

## Restricciones
Mantener el estilo cyberpunk y los colores por rol. Trabajar en una rama nueva, sin commit hasta que lo revise.

## Criterios de terminado
- Sin scroll horizontal a 375 px; los bloques se pueden tocar sin errores.
- `npm run lint` y `npm run build` sin errores; revisado en el navegador en modo móvil (skill `verify-change`).

## Supuestos
- "Se ve feo" se refiere a que los bloques quedan apretados y los días se cortan.

## Preguntas
- ¿Hay algo específico que te moleste (tamaño de texto, días cortados, colores)?
```
