// users/{uid}.pushTypes lists the pushes the user's build can show and the user has left on. Builds
// from before it existed don't write it: they only show invite pushes, and turn those off by clearing
// their token instead, so a missing list means "invite" only.
export function acceptsPush(pushTypes: unknown, type: "invite" | "request"): boolean {
  return Array.isArray(pushTypes) ? pushTypes.includes(type) : type === "invite";
}
