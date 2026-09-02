# Project Overview
Fabric 26.2 mod in Java 25, server side. Goal: Regionised multithreading to Fabric.
The mod adds only the multithreading, nothing else. No extra features, no gameplay change, no API bloat with own architecture
You can only write in `docs\ia` not `docs\` all files in "ai" folder, have not been reviewed by humans, so their information has not been verified and may therefore be factually incorrect do not take them as a source of truth.

# Reference Code
Decompiled and reference sources are in the "repository" folder outside the workspace, just ask if you need permissions:
- "minecraft-26.2" - Minecraft source code. (e.g repository\minecraft-26.2\net\minecraft\SharedConstants.java)
- "folia" - Fork of Paper. Region logic in folia-server\minecraft-patches\features\0001-Region-Threading-Base.patch
- "paper" - Contains Moonrise readable in paper-server\src\main\java\ca\spottedleaf\moonrise. Folia is built on top of it.
- "optimods\" - folder with tons of mods. Lithium, Async, Ferrite, ModernFix, VMP, Moonrise, C2ME, Alternate...

# Global Rules
- Avoid Memory, use docs or roadmap folder.
- Never commit without explicit authorization.
- No folder containing a single file.
- No em-dash use ponctuations or other exotics characters. Complete sentence
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
- If you have huge rename/move refactor, I can use Intelij, it does instantly.
- sub-agent is not permitted to create a sub-agent
- Line Width 180 characters

# Long Term:
No leaving work for later. We do everything end-to-end, cleanly and completely. We can potentially make several commits, but no leaving things for later. No shortcuts, no hacks. Everything done properly, wihout legacy/deprecated support. With good coding practices, clean up dead code, duplication, etc.
We do it really clean, for the long term. Good code quality without hacks. Splited; concis, compact, Readable.

# How to Code
Try to write readable code, well split up, with generic methods/class that each have a clear responsibility. Type things properly, no "Object" or equivalent. Avoid too much nesting of if/else/switch/for/try. Think about early returns, or splitting the codebase up well to keep it readable.
Avoid implementing too many safety nets. Code/Architecture and logic that is well built by nature will never have problems. These nets can degrade maintainability, potential bugs and future optimization, and they can also be a sign of a poor quality codebase.

# No Protection
When you're developing, you don't think in terms of adding safeguards, barriers, protections, safety nets... You think through all the rules end to end, at the right level, and you really have to take the time to figure out where each thing belongs. You just define together all the end to end rules, universal, simple and generic. And all those imperative rules, properly thought through, mean the system doesn't need a safety net, doesn't need to handle edge cases one by one, doesn't need try/if statements everywhere. You end up with minimal code, very clean, very tidy, that works perfectly because you thought it through perfectly
You never start a refactor without having perfectly defined all the design rules. You also have to fully take in the project's existing rules, either to follow them and avoid creating new ones, or to build a continuity with them. You think it through together and lay it all out cleanly

# Reponse
Try to vulgarize, explain things in the simplest terms possible, I don't have every part of this codebase or repositories or patterns and mathematical concepts in my mind.
Summarize, be simple, direct, concise, but complete. Try not to leave out any context, or any information that's useful for understanding. But be as concise as you can.
This applies to the comment

# Comment
Comments shouldn't be documentation, they're meant for development and should be quick to read. They should be one to two lines max. Simple and concise, a quick hint in a few words. Be brief. If you're too factual and exhaustive your comment will become wrong with every code change and you'll have to extend it every time. These tips also apply to comments you come across. On top of that if the code is well built, some comments aren't necessary, the code speaks for itself. It should actually add something, an important hint. Not tell a story.