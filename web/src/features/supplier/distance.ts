// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5), 2026-09-27.
// Scope: client-side Haversine distance calculation for issue #133's
// "sort by distance" follow-up (team decision). Used only to render a
// human-readable "X m/km away" label on each card — the authoritative
// nearest-first ordering comes from the backend's own distance query
// (GET /suppliers?lat=&lng=), not from this. Formula/constant (Earth
// radius 6,371,000 m) mirrors supplier-service's SuppliersRepository
// so the displayed label and the actual sort order agree.
// Reviewed by: [pending]

const EARTH_RADIUS_METERS = 6371000

function toRadians(degrees: number): number {
  return (degrees * Math.PI) / 180
}

export function haversineDistanceMeters(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number,
): number {
  const dLat = toRadians(lat2 - lat1)
  const dLng = toRadians(lng2 - lng1)
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) * Math.sin(dLng / 2) ** 2
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return EARTH_RADIUS_METERS * c
}

export function formatDistance(meters: number): string {
  if (meters < 1000) return `${Math.round(meters)} m away`
  return `${(meters / 1000).toFixed(1)} km away`
}
