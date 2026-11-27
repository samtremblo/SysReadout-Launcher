# SysReadout Launcher — user manual

SysReadout Launcher (SRL) is a home screen: a short list of your apps in front of a live, terminal-style readout of what the phone is doing. This manual covers everything in the app, from setting it as your home screen to the system monitor.

- [1. Make it your home screen](#1-make-it-your-home-screen)
- [2. The home screen](#2-the-home-screen)
- [3. The app drawer](#3-the-app-drawer)
- [4. Settings](#4-settings)
- [5. The log](#5-the-log)
- [6. The system monitor](#6-the-system-monitor)
- [7. Appearance](#7-appearance)
- [8. Lock screen](#8-lock-screen)
- [9. Battery use](#9-battery-use)
- [10. Troubleshooting](#10-troubleshooting)
- [11. Privacy](#11-privacy)

## 1. Make it your home screen

After installing, open **Settings › Apps › Default apps › Home app** and choose **SysReadout**. (The exact path differs a little between phone makers; searching settings for "home app" finds it.) Until then you can also open SysReadout from your current launcher to try it.

To go back, pick your previous launcher in the same place.

## 2. The home screen

The home screen has three layers: the **log** in the background, an optional **clock and date**, and your **menu entries** (pinned apps) on top.

| Gesture | Does |
|---|---|
| Swipe up | Open the app drawer |
| Swipe down | Open the notification shade |
| Swipe left / right | What you assign under settings › gestures: an app, the app drawer, notifications, quick settings, lock screen, SysReadout settings, flashlight on/off, camera, phone dialer, alarms or calendar |
| Double-tap empty space | Lock the screen (needs the lock service, see below) |
| Long-press empty space | Open settings |
| Tap the clock / date | Open your alarms / calendar |

**Menu entries.** Pin apps from the drawer (long-press an app › *pin to home*). Entries are separate text blocks; you can add as many as fit the free height of the screen, and the drawer tells you when there's no room left. Long-press an entry to rename it, move it up or down, remove it, or open its app info.

**Double-tap to lock** uses a small accessibility service whose only job is to lock the screen; it reads no screen content. Switching the option on takes you to Android's accessibility settings to enable *SysReadout: double-tap to lock*.

## 3. The app drawer

Swipe up. The keyboard opens straight into search (you can turn that off).

- Search ignores accents, case and spacing (`whats app` finds *WhatsApp*). Matches are listed best first:
  1. the exact name, then names that **start** with what you typed;
  2. a **word** that starts with it (`maps` finds *Google Maps*);
  3. **initials**, including joined-up words (`gm` finds *Google Maps*, `wa` *WhatsApp*, `yt` *YouTube*);
  4. **several words**, each starting a word in order (`goo st` finds *Google Play Store*);
  5. a contact's **phone number** containing the digits you typed (3 or more);
  6. names that **contain** it anywhere.

  Contacts' **nicknames** count like their names: `bob` finds *Robert Smith*, shown as `Robert Smith · Bobby` so you can see why.

  Within each step, what you open most comes first (*sort by history*), then shorter names. If nothing matches that way, SysReadout guesses: letters in order (`ytb` → *YouTube*) or a small typo (`instagrem` → *Instagram*). Guesses never open by themselves.
- **Sort by history** (settings › drawer, on by default): matches stay grouped by how well they match (start, initials, anywhere), and within each group the apps and shortcuts you open most, and lately, come first, so the Go key usually opens the one you want. Each launch counts 1 and fades by half every week. Only apps and shortcuts are recorded, never contacts, on the phone only (and in your backups). *Clear history* forgets it; switching the option off also stops recording.
- Before you type, the drawer lists your most used apps and shortcuts under `# frequent` (with *sort by history* on), then everything under `# all`.
- As you type, the rest of the top result's name appears dimmed after your text (`ins`*tagram*), or `→ name` when it matched by initials or partway through the name. That's what the Go key opens.
- When there is only one match (an app, a shortcut or a contact), it opens by itself (*launch single match*, can be turned off). The keyboard's Go key opens the first result.
- Long-press an app to pin it, rename it, hide it, open its app info or uninstall it.
- Apps from a work profile are marked `[w]`.
- **Shortcuts**: use *add to home screen* in another app (for example a web page in Chrome) and the shortcut appears in the drawer under `# shortcuts`, searchable like apps; long-press it to remove it. Android only gives shortcuts to the default home app, so this needs SysReadout set as home, and shortcuts you added while another launcher was default stay with that launcher: add them again. *App shortcuts in search* (settings › drawer, off by default) also lists apps' own shortcuts, such as *new incognito tab*, while you type.
- **Google search fallback** (settings › drawer, off by default): when no app or contact matches, the drawer shows *search google for "…"*. Tap it or press Go to open the search in your browser. What you typed goes to Google only when you do that; SysReadout itself makes no network request.
- **Contacts** (settings › drawer › *search contacts*, off by default): matching contacts are listed under `# contacts`, below the apps, with the same matching rules. Tapping one does what *tapping a contact* (just under the switch) says: *opens it* in your contacts app (the default), *calls* (opens the dialer with the number filled in; SysReadout places no calls) or *texts* (opens your messaging app). A contact without a phone number always opens. Long-press for the other actions: *open contact*, *call*, *text*, or *hide from search*. Hidden contacts are listed under settings › drawer, each with *unhide*; SysReadout remembers them only by Android's internal contact id (no name or number), and they aren't in backups. Switching it on asks for the contacts permission. Contacts are read into memory while the option is on and are never saved or sent anywhere. With it on, *launch single match* opens whatever is the only match: an app, a shortcut, or a contact (doing the *tapping a contact* action; *calls* only opens the dialer, it never places the call); the Go key opens the first app, or does the tap action on the first contact if no app matches.

Hidden apps are listed under settings › hidden apps, where you can bring them back.

## 4. Settings

Long-press an empty part of the home screen. The header (`sysreadout --config › …`) shows where you are; tap it or press Back to go up a level.

- **screens › appearance**: presets, colours, fonts, CRT effects ([section 7](#7-appearance)).
- **screens › log**: banner, rows, system monitor, stream ([sections 5](#5-the-log) and [6](#6-the-system-monitor)).
- **screens › shizuku**: the optional deeper monitor, with a guided setup ([section 6](#shizuku)).
- **home**: clock, date, log background on/off, entry style, horizontal alignment, vertical position.
- **lock screen**: [section 8](#8-lock-screen).
- **drawer**, **gestures**, **hidden apps**, **about & licenses**.
- **backup**: *save backup* writes every setting (home, drawer, log, look, presets, monitor), the launch history and your imported fonts to a `.json` file wherever you choose; *restore backup* reads one back and replaces your current settings after asking. Contacts, log data and wallpaper images aren't in it. Pinned apps that aren't installed on the phone you restore to simply don't show.

**Entry styles** keep menu text readable over the log: *frosted* (blurred glass), *highlight* (a solid box), *inverted* (reverse video) or *bare*.

## 5. The log

The log has up to four parts, top to bottom: the **banner**, the **pinned rows**, the **monitor tables** and the **stream**.

### Layout: classic or feed

Settings › log › *layout*.

- **classic** (default): the banner, then the rows, the tables with their headers, and the stream, each in its own place. With many rows switched on, whatever doesn't fit is cut off at the bottom.
- **feed**: one list without headers or separators, where every line says what it is (`proc  System UI  cpu 11.5%  rss 172M`, `conn  Signal → …`, `scrn  Firefox  1h12m on screen today`). It never runs out of room:
  - something new (an event, a row you switch on, a new process or connection) enters at the top and pushes everything else down; lines that no longer fit fall off the bottom;
  - a line already on screen updates where it is, so each item appears once;
  - a line that fell off comes back at the top the next time its value changes, so values that keep changing stay visible while static ones make room;
  - items that no longer exist (an exited process, a closed connection) disappear.

  *New rows appear at* switches the entry edge to the bottom, like a terminal's `tail -f`. The banner stays at the top either way, and the lock-screen snapshot follows the layout you pick.

### Banner

Free text above everything else, like a terminal's boot header. Edit it under log › banner › *edit text*. These placeholders are filled in live:

| Placeholder | Becomes |
|---|---|
| `{year}` | the current year |
| `{date}` / `{time}` / `{weekday}` | today's date, the time (HH:mm), the day's name |
| `{device}` | the name you gave the phone in Android settings (usually its model name) |
| `{model}` / `{maker}` | the model and manufacturer codes |
| `{android}` / `{api}` / `{kernel}` / `{build}` | Android version, API level, kernel version, build id |
| `{uptime}` / `{battery}` | time since boot, battery level |

For example:

```
ASNIDEV INDUSTRIES OPERATING SYSTEM
COPYRIGHT 2026-{year} ASNIDEV INC.
- {device} -
```

The banner has its own font and colour (appearance › text › banner) and can be aligned left, centre or right.

### Pinned rows

Rows that stay in place and update every few seconds. Under log › *pinned rows* you can switch each one on or off (tap it) and reorder the ones you show (↑ ↓). A row that needs a permission asks for it the moment you switch it on; until it's granted it shows `needs …` instead of a value. Signal strength is written out in words (*very strong* … *very weak*, *no signal*) rather than drawn as bars.

| Row | Shows | Needs | On by default |
|---|---|---|---|
| `time` | time with seconds, UTC offset, unix epoch | — |  |
| `up` | uptime and how much of it was awake | — | yes |
| `boot` | boot count and when this boot started | — |  |
| `os` | Android version, API level, security patch | — | yes |
| `kern` | kernel release | — | yes |
| `props` | A/B slot, verified boot, bootloader lock, treble, first API, build type | — |  |
| `dev` | manufacturer, model, codename | — |  |
| `self` | SysReadout's own process: pid, memory, threads | — |  |
| `cpu` | online cores, current clock range, governor | — | yes |
| `cores` | clock of every CPU core; with Shizuku, its load in percent | — |  |
| `load` | load average and process count | Shizuku |  |
| `soc` | chipset and CPU architecture | — | yes |
| `gpu` | GPU, OpenGL ES and Vulkan versions | — |  |
| `mem` | available / total RAM, low-memory flag | — | yes |
| `vm` | memory detail: cached, active, dirty, slab | — |  |
| `swap` | swap (usually zram) in use | — | yes |
| `therm` | thermal status and throttling headroom | — | yes |
| `temps` | every hardware temperature sensor: cpu, gpu, skin… | Shizuku |  |
| `bat` | level, charge state, temperature, health | — | yes |
| `pwr` | voltage, current, watts, power source | — | yes |
| `chg` | charge counter, estimated capacity, cycles, time to full | — |  |
| `mode` | battery saver, doze, do-not-disturb, ringer | — |  |
| `net` | connection type and live throughput | — | yes |
| `wifi` | Wi-Fi signal, link speed, band | — | yes |
| `cell` | mobile operator, network type, signal | — |  |
| `ip` | IP address and gateway on the local network (Wi-Fi, mobile or Ethernet); with a VPN on, also the VPN's address (`vpn …`) | — | yes |
| `radio` | airplane mode, bluetooth, NFC, location on/off | — |  |
| `rf` | mobile signal quality: RSRP, RSRQ, SINR (LTE/5G) or RSSI | — |  |
| `link` | network type incl. 5G NSA/SA and LTE-CA, carrier bandwidths | phone |  |
| `tower` | serving cell: technology, band, channel, PCI, area code | location |  |
| `ssid` | Wi-Fi name, BSSID, channel, Wi-Fi standard | location |  |
| `aps` | Wi-Fi networks nearby and the strongest | location |  |
| `data` | traffic since boot, total and mobile | — |  |
| `fs` | internal storage free / total | — | yes |
| `sd` | removable storage free / total | — |  |
| `disp` | resolution, refresh rate, density, brightness | — |  |
| `audio` | media and ring volume, audio output | — |  |
| `media` | what's playing and in which app | notification access |  |
| `alarm` | next alarm | — |  |
| `env` | light, pressure, altitude, temperature, humidity sensors | — |  |
| `compass` | heading, pitch and roll | — |  |
| `moon` | moon phase and days to the next full moon | — |  |
| `bt` | connected Bluetooth devices and their battery | Bluetooth |  |
| `debug` | adb, wireless debugging, developer options, USB | — |  |
| `apps` | launchable, hidden and work-profile app counts | — |  |
| `gps` | GPS fix: coordinates, altitude, accuracy, speed (GPS on while visible) | location |  |
| `gnss` | satellites used / in view per system: GPS, GLONASS, Galileo, BeiDou… | location |  |
| `sun` | sunrise, sunset and day length where you are | location |  |
| `today` | screen-on time and unlocks today | usage access |  |
| `month` | data used this month, Wi-Fi and mobile | usage access |  |
| `steps` | steps today and since boot | physical activity |  |
| `ntf` | notifications showing now and received today | notification access |  |

*Refresh every* sets how often rows update (1–10 s).

### Stream

The scrolling part at the bottom: events, newest at the bottom. Timestamps are off by default (log › stream › *timestamps*). What appears there is chosen under log › system monitor › *stream events*:

| Key | Event |
|---|---|
| `net` `power` `bat` `therm` `mem` `pkg` | network changes, charger plugged/unplugged, battery every 5%, thermal status, low memory, apps installed/removed |
| `fg` `svc` `scrn` `lock` | app switches, foreground services starting/stopping, screen on/off and unlocks (usage access) |
| `proc` `conn` `logE` `logW` | app processes starting/exiting, new connections, system errors and optionally warnings from logcat (Shizuku) |
| `dns` | which app looked up which server name (DNS monitor) |
| `ntf` | which app posted a notification; its title only if you turn *show titles* on (notification access) |
| `shizk` `err` `tip` | Shizuku connecting or going away; a part of the monitor that stopped after an error, and when it retries; a one-time tip |

Events that happen while another app is open are collected the next time you return home, with their original times.

## 6. The system monitor

Android lets a normal app see only its own processes, so the deeper parts of the monitor use access you grant separately. Everything is optional; the status of each is shown under log › *system monitor*.

### Usage access

Settings › log › system monitor › *usage access*. Unlocks: screen-time and traffic-per-app tables, the `today` and `month` rows, and the app-switch/service/screen events.

### Shizuku

[Shizuku](https://shizuku.rikka.app/) is a free app that lends SysReadout the access of `adb shell`, only while you allow it. It is optional and off until you switch it on under **settings › shizuku**; everything else in SysReadout works without it. With it, the log also shows:

- **processes** sorted by CPU or memory (`top`), with real app names
- **connections**: every open TCP/UDP connection per app, with the server's name
- **wakelocks**: what is keeping the phone awake
- **battery drain per app** since the last charge, with what used it (screen, CPU, sensors…)
- rows: **load** average, **cores** (load per core instead of clocks), **temps** (every hardware temperature sensor)
- stream: process start/exit, new connections, system errors

**Setting it up.** Settings › shizuku shows a live checklist, *installed › running › allowed › connected*, with a button for the next step:

1. Install Shizuku (from GitHub, IzzyOnDroid or Google Play).
2. Start it. On Android 11 and newer no computer is needed: turn on developer options (Settings › About phone › tap *Build number* 7 times), connect to Wi-Fi and switch on *Wireless debugging*. In Shizuku tap *Pairing*, then in Wireless debugging tap *Pair device with pairing code* and type the code into Shizuku's notification. Pairing is done once; after that tap *Start*. On Android 10 and older Shizuku is started from a computer with `adb`. On a rooted phone Shizuku starts itself, or use Sui instead.
3. Allow SysReadout when Shizuku asks (*allow all the time*).
4. SysReadout connects by itself.

**After a reboot.** Without root, Android stops Shizuku at every reboot. On Android 13 and newer, switch on *Start on boot* in Shizuku's settings: it restarts itself over wireless debugging whenever the phone is on a Wi-Fi where you chose *Always allow on this network*. SysReadout notices when Shizuku comes back and reconnects. While Shizuku is away, one `shizk` row in the log says why instead of the tables.

**One-tap setup.** Once Shizuku is connected, *set up access with shizuku* can switch on SysReadout's other access for you: usage access, notification access and the double-tap lock service, the same switches you would flip in Android's settings. It lists what it will change and does nothing until you tap *switch on*. Those grants stay after Shizuku stops.

**If it misbehaves.** SysReadout never lets Shizuku hold up the home screen: every call is time-limited, a failed read keeps the last values instead of reporting everything gone, and a helper that keeps failing is left alone for a while (the `shizk` row says so) before SysReadout tries again. Switching *use shizuku* off stops all of it.

Server names come from reverse DNS by default (*hostnames (reverse dns)*), which often gives provider names such as `…1e100.net` (Google). The DNS monitor gives the names apps actually asked for.

### DNS monitor

Settings › log › *dns monitor*. It shows which app looks up which server (`Gmail → imap.gmail.com`), and gives the connections table real host names.

How it works: SysReadout starts a VPN that carries **only DNS**. Android sends apps' lookups to a resolver address inside it; each lookup is noted (app, name, returned addresses) and passed on unchanged to your network's normal DNS server. If the network names no DNS server (offline), the lookup goes unanswered, as it would without the monitor; there is no public fallback resolver. No other traffic goes through it, and nothing is sent anywhere else. This is the only feature that uses the internet permission.

Limits:

- Android allows one VPN at a time, so it can't run together with another VPN app.
- If Private DNS is set to a specific provider (Settings › Network › Private DNS), Android sends lookups there directly and the monitor sees nothing. *Automatic* or *off* works.
- Apps with their own encrypted DNS (some browsers) bypass it.
- It relays DNS over UDP only. An answer too big for one UDP packet comes back marked *truncated*, and the follow-up lookup over TCP is refused at once, so that one name fails quickly instead of hanging. This is rare (very large records only).

### Notification access

Needed for the `ntf` and `media` rows, the notification stream and the *notifications today* table. Notification titles are kept in memory only and shown only if you turn on *show titles*.

## 7. Appearance

Settings › appearance.

- **Presets**: *sysreadout* (default), *green p1* (green phosphor terminal), *amber p3* (DOS amber), *paper p4* (white phosphor), *minimal*, *arcade*, *lime*, *synthwave* (neon pink and cyan), *ice p7* (blue-white radar phosphor), *red alert* (all red, easy on night vision), *teletype* (warm ink on old paper) and *bubblegum* (pastel pixels). Tap to apply. *Save current as preset…* keeps your own; long-press one of yours to delete it.
- **Colours**: background, accent (prompt, cursor), backing (the tint behind menu entries).
- **Text**: clock, date, menu entries, drawer, log and banner each have their own font, size, bold, letter spacing, case (as-is / lower / UPPER) and colour, with a live preview.
- **Fonts**: 11 bundled (system mono/sans, JetBrains Mono, IBM Plex Mono, Space Mono, Share Tech Mono, VT323, Px437 IBM VGA, Major Mono Display, Press Start 2P, Silkscreen) plus *import .ttf / .otf* for your own. Long-press an imported font to delete it.
- **Menu entries › prefix**: text in front of every entry, such as `> `, `$ `, `C:\> ` or a number (`[1]`).

### OLED black

Appearance › *oled black* draws the background pure black whatever theme or preset you pick, so an OLED screen leaves those pixels switched off. Text, accent and backing colours stay as the theme sets them, the lock-screen snapshot follows, and switching it off brings the theme's own background back. For the most saving, leave glow and grain off too: they light pixels around the text.

### CRT effects

Appearance › *crt effects*: glow, scanlines, vignette, curvature and colour fringe (the last two need Android 13+), plus animated flicker and grain. *Menu, clock and date too* applies them to the whole home screen instead of just the log. Flicker and grain redraw the screen about 20 times a second while it's visible, so they cost battery.

## 8. Lock screen

Settings › *lock screen*:

- **leave alone** (default): SysReadout doesn't touch your lock-screen wallpaper.
- **your image**: pick a picture for the lock screen only.
- **log snapshot**: each time the screen turns off (at most once a minute), SysReadout draws the log, with your theme and CRT effects, as the lock-screen wallpaper. *Update now* redraws it immediately. *Private snapshot* (on by default) leaves out everything that says where you are or what you do: location, sunrise, network and Bluetooth names, IP, cell tower, media, notifications, the app, connection and DNS tables and the stream. Anyone holding the locked phone can read the lock screen, so switch it off only if that's fine with you.

Android's own clock and notifications are drawn on top. Only the lock screen is changed; the home screen is always SysReadout's own drawing.

## 9. Battery use

- The log runs **only while the home screen is visible**. In another app or with the screen off it uses nothing.
- Typical cost while visible is a few percent of one CPU core; more with every Shizuku table on at a 2-second refresh.
- Rows that turn hardware on say so: `gps` and `gnss` keep GPS active while visible; `env` and `compass` read sensors once a second.
- Flicker and grain are the only effects that redraw continuously.

The *battery drain per app* table (Shizuku) shows SysReadout's own share too.

## 10. Troubleshooting

| Symptom | Fix |
|---|---|
| A row shows `needs … permission` | Tap the row in settings › log to switch it on again and allow the permission, or grant it in Android's app settings. |
| Monitor tables are missing | They appear only when their source is available: check *usage access* and *shizuku* under settings › log › system monitor. |
| `shizk  not running` after a reboot | Start Shizuku again in its app, or let it start on boot (settings › shizuku › *after a reboot*). |
| The home screen says *safe mode after repeated crashes* | SysReadout crashed twice right after starting, so it came back without the log, Shizuku, the DNS monitor and your look. Tap the note to switch them back on. The crash report is under settings › about, to copy or share with a bug report. |
| Accessibility or notification access is greyed out (*restricted setting*) | Android 13+ blocks these for apps installed from a downloaded APK. In SysReadout's app info, open the ⋮ menu › *Allow restricted settings*, or use *set up access with shizuku*. |
| The DNS monitor switches itself off | Another VPN took over, or Private DNS is set to a provider. |
| `wifi` shows no network name | The `ssid` row needs the location permission *and* location switched on. |
| Lock-screen snapshot doesn't show | Some phones override lock-screen wallpapers in their own theme settings; set the lock screen there to use the wallpaper. |
| Rows are cut off at the bottom | Switch settings › log › *layout* to **feed**, or show fewer rows. |
| Text over the log is hard to read | Use the *frosted* or *highlight* entry style, or turn down CRT glow. |

## 11. Privacy

SysReadout collects nothing and sends nothing: no ads, analytics or tracking. Everything it shows is read on the phone and kept in memory while the home screen is visible. The only network use is the optional DNS monitor, which passes your apps' own lookups to your normal DNS server, and optional reverse-DNS lookups of connection addresses (made by Shizuku's helper process). If SysReadout crashes, the crash report stays on the phone (settings › about) until you share or delete it.
