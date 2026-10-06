# mein-training-main

<!-- queue-program: next one -->
## "next one": the work queue

This project's work queue runs on the Queue Program (`C:\Users\ichbi\Desktop\Queue Program`); its items, reports and locks are in `_queue/`. When a message is just "next one", run `PYTHONUTF8=1 python _queue/tools/qtool.py check` from this folder. If its decision is `refuse`, `finished`, `paused` or `stopped`, reply with one short sentence and stop; otherwise read every file its `read` list names and follow them exactly. It runs unattended: never ask anything.

When the user hands over prompts "for the next one mode" (or for the queue), read `C:\Users\ichbi\Desktop\Queue Program\ADDING.md` and `_queue/PROJECT.md` and follow them: keep their text, cut it into chat-sized items, put them in order and register them. Ask only about a choice that can't be undone.

For the queue's status: `python _queue/tools/qtool.py status`.
