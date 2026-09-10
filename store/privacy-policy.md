# Privacy Policy, French Dictée Practice

_Published at: https://johnboniello.com/dictee/privacy.html — use this as the Play Console "Privacy Policy URL"._

Last updated: September 10, 2026

This policy explains what information French Dictée Practice collects, and it explains why I built the app the way I did. I built this app for my own child to practice her weekly French spelling words, and my approach to privacy is straightforward: I do not collect personal information. There are no accounts, no email addresses, no names, and no advertising identifiers; nothing that identifies you or your family passes through this app.

Below is exactly what the app does and does not do with data.

## What stays on your device

Your word lists and practice stats, including which words you have gotten right or wrong and which ones are pinned for review, are stored locally on your device. I do not see this data, and it does not leave your device unless you turn on the optional sync feature described below.

## Scanning a word list with the camera

The app can use your camera to scan a photo of a word list and turn it into text. This happens entirely on your device, using Google's ML Kit text recognition. The photo is processed locally and is not uploaded anywhere, to me or to anyone else. Camera access is optional and is only requested when you tap "Scan a list."

## Optional sync, for sharing a list across devices

If you turn on sync, the app generates a random code on your device: two French words plus six random characters, for example something like chat-bleu-x7k2p9. This code is not tied to your name, email, device, or any other identifier; it is simply a shared key.

If you share that code with another device, so your child can practice on a second phone or a grandparent's tablet, both devices can read and write the same word list and practice stats using that code. That data is stored on Cloudflare's infrastructure, specifically a Workers KV store, under the code itself; there is no account behind it, because there are no accounts.

A few things worth stating plainly about this feature:

- Anyone who has the code can access that list. This is why the code is long and random rather than short and guessable.
- Cloudflare, as the hosting provider, may log standard web request information, such as IP addresses, as part of normal security and abuse prevention. This is infrastructure level logging on their end, separate from anything the app itself collects or uses.
- If you stop using a sync code, the associated data is not automatically deleted; it simply stays inactive under that code.

## What I do not do

- I do not run ads.
- I do not use analytics or trackers.
- I do not sell or share data, and I do not use it for advertising.
- I do not require or offer account creation.

## Children's privacy

This app was built for a child to use, and that shaped every decision above. There is no login, no way to identify a specific child, and nothing collected that could be used to contact or track a child. The optional sync feature is designed to be shared between family members using a code, not a personal account.

## Changes to this policy

If anything about how the app handles data changes, I will update this page and the date at the top.

## Contact

Please contact me directly with any questions about this policy: johnboniello@gmail.com
