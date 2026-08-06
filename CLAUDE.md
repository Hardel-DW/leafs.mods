# Project Overview
Fabric 26.2 mod in Java 25, server side. The goal: bring regionised multithreading to Fabric, like Folia, but completely rethought for the modern Minecraft architecture. Everything through Mixins, no fork, no patched jar, just a mod.
This is not a port of Folia patches. We take the concepts (regions, one tick loop per region, no global main thread) and we redesign them for vanilla 26.2 + Fabric.
The mod adds only the multithreading, nothing else. No extra features, no gameplay change, no API bloat.
The documentation entry point is docs\README.md.

# Reference Code
Decompiled and reference sources are in the "repository" folder outside the workspace, just ask if you need permissions:
- "minecraft-26.2" - Minecraft source code. (e.g repository\minecraft-26.2\net\minecraft\SharedConstants.java)
- "folia" - Fork of Paper, patch format. The whole region logic is in folia-server\minecraft-patches\features\0001-Region-Threading-Base.patch
- "paper" - Contains Moonrise readable in paper-server\src\main\java\ca\spottedleaf\moonrise (concurrent chunk system, concurrency utils). Folia is built on top of it.

# Global Rules
- Avoid Memory, use docs or roadmap folder.
- No em-dash use ponctuations. Complete sentence
- No redundancy, we must avoid duplicating truth sources.
- No function/variable with a single line/reference. Except Getter/Setter...
- Avoid over engineering.
- No support of Legacy/Deprecated.
- A class should have a single dominant responsibility.
- Avoid dirty code / temporary code.
- It's better to tell me what you have in mind before doing it.
- Don't just write code that fixes a problem immediately, think long term and consider all possible future scenarios.
- Don't lie, prefer to tell the truth even when it's negative, don't please me just to please me, we must work factually.
- Try to criticize my choices which can sometimes go in the wrong direction.
- Avoid adding too many unnecessary comments that serve no purpose.
- Prioritize the OOP approach. Don't make everything in a static class. Use a correct Pattern. (Static is good but not for everything)
- Avoid unchecked, UNCHECKED_CAST find good architectural solutions that avoid them as much as possible.
- Avoid Inline Import.
- No Commented Code.
- If you have huge rename/move refactor, I can use Intelij, it does instantly.

# Long Term:
No leaving work for later. We do everything end-to-end, cleanly and completely. We can potentially make several commits, but no leaving things for later. No shortcuts, no hacks. Everything done properly.
With good coding practices, clean up dead code, duplication, etc.
We do it really clean, for the long term. Good code quality without hacks.
Removing code can sometimes require writing a bit of new code to eliminate a lot more. We're not aiming for fast delivery but for code quality over multiple years.
