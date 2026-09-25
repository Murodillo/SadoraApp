import { describe, expect, it } from 'vitest'
import { lengthProblem, limits } from './limits'

describe('lengthProblem', () => {
  it('measures the trimmed text, as the server does', () => {
    expect(lengthProblem('  a  ', limits.postMin, limits.postMax)).toBe('Kamida 2 ta belgi')
    expect(lengthProblem(' ok ', limits.postMin, limits.postMax)).toBeNull()
  })

  it('names the ceiling when the text is over it', () => {
    expect(lengthProblem('x'.repeat(limits.commentMax + 1), limits.postMin, limits.commentMax)).toBe("Eng ko'pi 1000 belgi")
    expect(lengthProblem('x'.repeat(limits.commentMax), limits.postMin, limits.commentMax)).toBeNull()
  })

  it('calls a required field empty rather than short', () => {
    expect(lengthProblem('   ', 1, limits.doctorWorkplaceMax)).toBe("To'ldirilishi shart")
    expect(lengthProblem('', 0, limits.doctorBioMax)).toBeNull()
  })
})
