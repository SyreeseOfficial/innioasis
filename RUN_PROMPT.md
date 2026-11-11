Read CLAUDE.md files, PRD.md, CONTEXT.md, BUILD_PLAN.md and NOTES.md (if it exists). Then continue the Y1 music app build from BUILD_PLAN.md. Start at the first phase that has no `phaseN:` commit in `git log`.

How to work:
1. Do one phase at a time, in order. Build it, run its TEST on the device over `adb`, then move straight to the next phase. Don't wait for me between phases. Keep going until the whole plan is done or you hit a stop condition below.
2. Run each TEST for real: build, install, launch, and check with `adb logcat`, `adb shell` and screenshots (`adb exec-out screencap -p`). Don't claim a test passed unless you saw the output. If a test needs my hands (a physical button press, a Bluetooth headphone, listening to audio), tell me exactly what to do, then wait for my answer.
3. When a phase's TEST passes, commit with the message `phaseN: summary` plus the Co-Authored-By line, add the tag from the plan, and `git push` (with `--tags`). Never commit failing code to main. Never commit `backups/*.apk`, keystores, or music files.
4. Small issues (compile errors, a crash, a layout bug, a wrong keycode, a slow list): fix them yourself. Try up to 3 different approaches. If it's still broken after that, treat it as major.
5. Major issues: stop and ask me. That means: a phase fails its test and the plan's fallback doesn't cover it, a spec decision has to change (PRD scope, dropping covers, dropping FLAC), anything that could brick or soft-brick the device, or you're unsure about something that costs more than 30 minutes to redo. Ask one short question with your recommendation first and 2 options.
6. Always ask before: setting the app as the launcher, running `pm disable` on any system package, pushing or deleting anything in `/system`, editing `CONTEXT.md` facts about the device, or force-pushing. Before the launcher step, write the recovery commands into `NOTES.md` and prove `adb root` still works.
7. Keep the work minimal: plain Java, no libraries, no Gradle, fewest files, no speculative features. Anything in the PRD non-goals stays out.
8. Keep `NOTES.md` current: key map, spike results, timings, bugs found and how they were fixed, decisions made. Update `CONTEXT.md` "Done so far" after the last phase.
9. Context management: if the session gets long, `NOTES.md` plus `git log` must be enough for a fresh session to pick up exactly where you left off. Write there before you run out of room, not after.
10. Report in a short status after each phase: phase N of 10 done, what now works (and how I can see it on the device), what's next, one line. No long recaps. At the end: summary, anything I need to test by hand, and the recovery commands.

Start now. First action: check `git status`, `git log --oneline`, and `adb devices`, then begin the next unfinished phase.
