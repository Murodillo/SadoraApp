/**
 * Up to two letters for a face that has no photo: the first letter of the first two
 * words, skipping a title like "Dr." so "Dr. Nilufar Karimova" is "NK", not "DN".
 * Uzbek apostrophes (O'g'iloy, G'ulnora) belong to the letter and are dropped.
 */
export function initialsOf(name: string | null | undefined): string {
  const words = (name ?? '')
    .split(/\s+/)
    .map((word) => word.replace(/^[^\p{L}\p{N}]+/u, ''))
    .filter((word) => word.length > 0 && !/^(dr|doc|prof)\.?$/i.test(word))
  const letters = words.slice(0, 2).map((word) => Array.from(word)[0]?.toLocaleUpperCase('uz') ?? '')
  return letters.join('') || '?'
}
