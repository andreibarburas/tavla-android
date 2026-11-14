# Changelog

All notable changes to Tavla are documented here.

## [Unreleased]

### Added
- `tavla://card/{id}` deep links, so other BarburasLab apps (starting with Vinci's "Linked items") can open a specific card directly. The id is Deck's own stable card id, so it survives the card moving between lists; the board it lives on is looked up locally before opening

### Changed
- The "link a contact" picker, and a card's own "Linked contacts" list, now show each contact's actual photo instead of a generic icon
- A linked contact can now be removed with a tap on its own row, not only by long-pressing it
- When a card's contact link fails to sync to Nextcloud (most commonly: this device is in Local mode, with no Nextcloud account to push to), that's now shown directly under "Linked contacts" instead of failing silently -- which is also why a card's contact link can look fine in Tavla but never show up on the contact's page in Vinci

### Changed
- An assignee's (or commenter's) own Nextcloud profile photo is now shown instead of their initials, wherever Tavla shows who's on a card -- falls back to the initials circle for anyone without one set

### Changed
- A card's Description, Checklist, Attachments, Linked items, Linked contacts and Comments sections are now each boxed, matching the look of Due/Assigned/Time tracking and Vinci's own contact screen -- section labels stay above, un-boxed, as before

### Changed
- "Add linked item" now also offers Koto as a destination, alongside Vinci and Nóta

### Added
- "Send to Nóta" and "Send to Koto" from a card's menu, sending its title and description over as a new note or task

### Fixed
- Linking a contact to a card could silently never reach Nextcloud: the push used an ETag-conditional PUT to guard against another device overwriting a conflicting edit, but there was never anything to actually merge on a conflict (Room is the sole source of truth for these links either way), and on some Nextcloud setups -- a proxy that tags GET and PUT responses with different ETags for the same file, which gzip-compressing proxies commonly do -- even the one built-in retry kept 412'ing, abandoning the push every time. The write is now unconditional, since the conflict handling was never protecting anything real

## [1.0.0] - 2026-10-02

First release.

### Added
- Nextcloud Login Flow v2 sign-in, with the poll URL rewritten to the address you typed (works behind Tailscale and custom subdomains) and a check that the Deck app is enabled on the server
- Board overview with search, a cross-board "Due soon" strip, card and due counts per board, and archived boards on demand
- Create, edit (title and color), archive, restore and delete boards
- Board view with swipeable lists, list tabs with card counts, and filters for assigned to me, with due date, overdue and per label
- Add, rename and delete lists
- Add cards, and move them between lists with a long press
- Card detail with editable title and description, list switcher, labels (including creating new ones), assignees, due date and time, archive and delete
- Interactive checklists that read and write Deck's Markdown task items, so they stay in sync with Deck on the web
- Comments: read, post and delete your own
- Offline copy of all boards in a local database, so boards open instantly and stay readable without a connection
- Optional biometric app lock
- Theme (System, Light, Dark, OLED Black), text size, DM Serif Display + Inter Tight font pairing, and label visibility settings
- Plain HTTP support for private networks
- A FAB to add a card to the list you're currently viewing, without scrolling
- Board icons: pick a Material icon per board, or leave it on the automatic color glyph
- Board cover images: pick a photo from your device as a board's tile instead of an icon. It uses the Android photo picker, so no storage permission is requested. The image is downscaled and stored in your own Nextcloud (under `/Tavla/covers/`), cached on each device for instant display, so covers follow you across devices just like the rest of your settings. Change or remove one anytime from the board's menu; removing it deletes the file from Nextcloud too.
- Background sync on a configurable interval (15 min to 3 hours), so boards are never stale on open
- Local due-date reminders (1 hour and/or 1 day before, configurable), with no push server involved
- Share text or a link from any app straight into a new card
- Drag-and-drop: reorder cards within a list, and hold near the screen edge to move a card to the next or previous list; reorder lists from their tab menu
- Lightweight Markdown rendering for card descriptions (headings, bold/italic, links, lists, quotes)
- Attachments: add a photo from the camera or any file, view the list on a card, open or delete them
- "Done" status with a quick checkmark on the board and in the card, backed by Deck's own done field where the server supports it, plus a Done filter
- Each board's own color now accents its active list tab and its add-card button
- Haptic feedback on long-press-to-drag and on marking a card done; smoother list/card motion
- Linked items: attach a link to a Vinci contact, a Nóta note, or any other app to a card (needs matching support in those apps to open the exact item; falls back to opening the app)
- Linked contacts: link a card directly to a Vinci contact from your address book -- it then shows up on that contact's profile in Vinci
- Settings, board icons and card links now live in a small JSON file in your own Nextcloud (`/Tavla/tavla-settings.json`), so they follow you to a new device
- Search on the boards screen now matches every open card's title and description, not just board names and due cards, so Tavla doubles as a quick lookup tool
- Board insights: cards finished per week, current overdue count and a by-week breakdown of still-open cards, average time from creation to done, and the busiest lists -- all computed on-device from what's already synced
- Solidtime time tracking: connect a self-hosted Solidtime server with a personal access token (stored encrypted on the device), pick your organization, and link a board to a Solidtime project. Start and stop a timer from any card: the entry's description is pre-filled with the card title and is editable, billable defaults from the project and can be overridden per entry, and only one timer runs at a time (a timer started on the web is picked up and can be stopped from the app). While a timer runs, an ongoing notification shows the elapsed time with a Stop action, and a banner on the boards screen opens the card being tracked. Each card shows its total tracked time and a list of entries you can add, edit or delete by hand, and Tavla remembers which entries belong to which card even when titles change. Starting and stopping work offline and sync when the connection returns. Per-board options (all off by default): create a Solidtime task per card, tag entries with the list name, and auto-stop the timer when a card moves into a chosen list. Links live in their own database that survives app updates, and unlinking never deletes anything in Solidtime. All Solidtime mappings (board-project links, card-entry links and card-task links) sync to Nextcloud alongside your other settings, so per-card time history follows you to a new device.
