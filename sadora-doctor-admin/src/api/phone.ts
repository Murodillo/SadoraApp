/**
 * An Uzbek mobile number, in the one shape everything agrees on.
 *
 * A port of `uz.sadora.contract.UzbekPhone`, so the panel refuses exactly what the
 * server and the app refuse. The field holds `90 123 45 67`, the wire carries
 * `+998901234567`, and a doctor may paste either — or `998 90 123-45-67` from a
 * contact card. Change the operator list there and change it here.
 *
 * Two jobs, deliberately separate. [accept] is what the field calls on every keystroke:
 * forgiving, and it truncates. [parse] is what the request is checked with: strict, and
 * sixteen digits are a mistake, not a number with nine at the front.
 */

export const COUNTRY_CODE = '998'

/** Nine digits after the country code. Nothing longer is a number; it is a typo. */
export const NATIONAL_LENGTH = 9

/**
 * The two-digit codes an Uzbek mobile number can start with. Landlines are absent on
 * purpose — the code arrives by SMS.
 */
export const OPERATOR_CODES: ReadonlySet<string> = new Set([
  '20', '33', '50', '55', '77', '88', '90', '91', '93', '94', '95', '97', '98', '99',
])

const digitsOf = (raw: string) => raw.replace(/\D/g, '')

/**
 * What the field should hold after this keystroke: digits only, at most nine. A leading
 * `998` is dropped once there are more digits than a whole number, so pasting
 * `+998901234567` works and typing `99…` — a real operator code — is not eaten.
 */
export function accept(raw: string): string {
  const digits = digitsOf(raw)
  const national =
    digits.length > NATIONAL_LENGTH && digits.startsWith(COUNTRY_CODE) ? digits.slice(COUNTRY_CODE.length) : digits
  return national.slice(0, NATIONAL_LENGTH)
}

/** The nine national digits of a complete number, or null when `raw` is not one. */
export function parse(raw: string): string | null {
  const digits = digitsOf(raw)
  let national: string
  if (digits.length === NATIONAL_LENGTH) national = digits
  else if (digits.length === NATIONAL_LENGTH + COUNTRY_CODE.length && digits.startsWith(COUNTRY_CODE)) {
    national = digits.slice(COUNTRY_CODE.length)
  } else return null
  return OPERATOR_CODES.has(national.slice(0, 2)) ? national : null
}

/** `901234567` -> `90 123 45 67`. A part-typed number is grouped as far as it goes. */
export function format(raw: string): string {
  const d = accept(raw)
  let out = d.slice(0, 2)
  if (d.length > 2) out += ` ${d.slice(2, 5)}`
  if (d.length > 5) out += ` ${d.slice(5, 7)}`
  if (d.length > 7) out += ` ${d.slice(7)}`
  return out
}

export function isValid(raw: string): boolean {
  return parse(raw) !== null
}

/** True while it could still become valid — used to hold an error back while she types. */
export function isIncomplete(raw: string): boolean {
  return digitsOf(raw).length < NATIONAL_LENGTH
}

/** `+998901234567`, or null when `raw` is not a number. */
export function toE164(raw: string): string | null {
  const national = parse(raw)
  return national ? `+${COUNTRY_CODE}${national}` : null
}

/** `+998901234567` -> `+998 90 123 45 67`, for telling her where the code went. */
export function display(raw: string): string {
  const national = parse(raw)
  return national ? `+${COUNTRY_CODE} ${format(national)}` : raw
}
