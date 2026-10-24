const EARTH_M = 6_371_000;
export function haversineMeters(lat1, lng1, lat2, lng2) {
    const toRad = (d) => (d * Math.PI) / 180;
    const dLat = toRad(lat2 - lat1);
    const dLng = toRad(lng2 - lng1);
    const a = Math.sin(dLat / 2) ** 2 +
        Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
    return 2 * EARTH_M * Math.asin(Math.min(1, Math.sqrt(a)));
}
/** Rough open-now using Africa/Lagos wall clock vs store open/close (HH:mm:ss). */
export function isOpenNow(openAt, closeAt, timeZone = "Africa/Lagos") {
    const now = new Date();
    const parts = new Intl.DateTimeFormat("en-GB", {
        timeZone,
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hourCycle: "h23",
    }).formatToParts(now);
    const h = Number(parts.find((p) => p.type === "hour")?.value ?? 0);
    const m = Number(parts.find((p) => p.type === "minute")?.value ?? 0);
    const s = Number(parts.find((p) => p.type === "second")?.value ?? 0);
    const cur = h * 3600 + m * 60 + s;
    const toSec = (t) => {
        const [hh = "0", mm = "0", ss = "0"] = t.split(":");
        return Number(hh) * 3600 + Number(mm) * 60 + Number(ss);
    };
    const open = toSec(openAt);
    const close = toSec(closeAt);
    if (open === close)
        return true;
    if (open < close)
        return cur >= open && cur < close;
    return cur >= open || cur < close;
}
export function normalizeTime(t) {
    const parts = t.trim().split(":");
    if (parts.length === 2)
        return `${parts[0].padStart(2, "0")}:${parts[1].padStart(2, "0")}:00`;
    if (parts.length >= 3) {
        return `${parts[0].padStart(2, "0")}:${parts[1].padStart(2, "0")}:${parts[2].padStart(2, "0")}`;
    }
    return t;
}
//# sourceMappingURL=geo.js.map