# Borrowed Time

A daily limit for the apps that eat your day, enforced by your friends.

Everyone in a group of up to six picks the apps they want to limit and a daily total. When your time is gone, those apps are paused. To get more, you have to ask the group. One friend saying yes is enough, and you only get three asks a day.

## Install

Download `borrowed-time.apk` from the [latest release](../../releases/latest) on an Android phone (8.0 or newer) and open it. Android will ask you to allow installs from your browser the first time.

## What it needs

- **Usage access** to count time spent in the apps you pick. It cannot see what is inside them.
- **Display over other apps** so the pause screen can appear on top.
- Notifications and a battery exemption are optional but make asks arrive faster and keep the guard alive.

## What it can't do

- It can't stop you uninstalling it. Friends will see "Guard off" and when you were last seen.
- Some phone makers kill background apps aggressively. Turn off battery optimisation for Borrowed Time if the guard stops.
- The pause screen is an overlay, not a system lock.

## Honest limits

- Leaving the group or uninstalling stops the guard on that phone. Friends see the member go quiet ("Guard silent" after ten minutes without a check-in). Asks per day are counted per name in the group, so leaving and rejoining under the same name does not reset them.
- Days follow the phone's clock and timezone. Someone who changes the date or timezone can confuse the daily reset. This is a trust-based app: it works among friends, it is not tamper-proof.
- Asks reach a friend on their next check-in (every 15 seconds with the screen on, every five minutes with it off). There is no push service, so a phone in deep sleep can be slower.
- The group shares your name, limit, time used today, how many apps you limit, whether the guard is running, when your phone last checked in, and any note on an ask.

## How it works

The app counts foreground time from Android's usage log, the same source Digital Wellbeing uses. A small foreground service watches which app is in front and shows the pause screen when the allowance is spent.

Groups sync through a Supabase Postgres backend. All access goes through a handful of functions (`backend/schema.sql`), each checked against a per-device secret. Tables are not exposed directly.

Lowering your limit applies immediately. Raising it, or removing an app, starts at the next midnight, so you can't dodge the guard mid-day.

## Build

```
gradle :app:assembleDebug
```

Release builds read `BT_KEYSTORE`, `BT_KS_PASS`, `BT_KEY_ALIAS` and `BT_API_KEY` from the environment. CI does this from repository secrets.

MIT licensed.
