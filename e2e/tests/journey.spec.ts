// End-to-end browser tests against a running stack:  cd e2e && npm install && npx playwright install chromium && npm test
// Every step is something a real user does; console errors and 5xx responses fail the run.
import { expect, test, type Page } from '@playwright/test'

const B = process.env.BASE_URL ?? 'http://localhost:3001'
const nav = async (p, label) => { await p.getByRole('link', { name: label, exact: true }).first().click(); };
let mule = ''
type Step = [string, (p: Page) => Promise<void>]

const steps: Step[] = [
  ['landing renders with live feed', async p => { await p.goto(B); await expect(p.getByRole('heading', { level: 1 })).toContainText('milliseconds'); await expect(p.getByText('payments.scored').first()).toBeVisible() }],
  ['landing sections scroll into view', async p => { for (const id of ['pipeline', 'mules', 'copilot', 'architecture']) { await p.locator('#' + id).scrollIntoViewIfNeeded(); await p.waitForTimeout(400) } await expect(p.getByText('One payment looks harmless')).toBeVisible() }],
  ['"Open the console" goes to sign-in', async p => { await p.evaluate(() => scrollTo(0, 0)); await p.getByRole('link', { name: 'Open the console' }).first().click(); await expect(p).toHaveURL(/\/login$/) }],
  ['wrong password is rejected', async p => { await p.locator('input[type=password]').fill('wrong'); await p.getByRole('button', { name: /Sign in/ }).click(); await expect(p.getByRole('alert')).toBeVisible({ timeout: 10000 }) }],
  ['analyst signs in', async p => { await p.getByRole('button', { name: /Priya Raman/ }).click(); await p.getByRole('button', { name: /Sign in/ }).click(); await expect(p).toHaveURL(/\/app$/); await expect(p.getByRole('heading', { name: 'Live monitor' })).toBeVisible() }],
  ['live stream delivers payments', async p => { const start = p.getByRole('button', { name: 'Start traffic' }); if (await start.isVisible().catch(() => false)) await start.click(); await expect(p.locator('tbody tr').first()).toBeVisible({ timeout: 90000 }) }],
  ['launch a mule-ring attack', async p => { await p.getByRole('button', { name: 'Mule ring' }).click(); await p.waitForTimeout(1500) }],
  ['cases queue lists cases', async p => { await nav(p, 'Cases'); await expect(p.getByRole('heading', { name: 'Investigation cases' })).toBeVisible(); await expect(p.locator('tbody tr').first()).toBeVisible({ timeout: 10000 }) }],
  ['open a case with its network graph', async p => { await p.locator('tbody tr a').first().click(); await expect(p.getByRole('heading', { name: /Case #/ })).toBeVisible(); await expect(p.getByText('Payment network')).toBeVisible() }],
  ['copilot writes a cited brief', async p => { await p.getByRole('button', { name: /brief/ }).click(); await expect(p.getByText(/Sources \(\d+\)/).first()).toBeVisible({ timeout: 20000 }) }],
  ['try a stolen-phone payment: not approved', async p => { await nav(p, 'Try a payment'); await p.getByRole('button', { name: /Phone stolen/ }).click(); await p.getByRole('button', { name: /^Pay ₹/ }).click(); await expect(p.getByText(/^(BLOCK|REVIEW)$/).first()).toBeVisible({ timeout: 10000 }) }],
  ['try an everyday payment: approved', async p => { await p.getByRole('button', { name: /Everyday payment/ }).click(); await p.getByRole('button', { name: /^Pay ₹/ }).click(); await expect(p.getByText('fraud probability')).toBeVisible(); await expect(p.getByText(/^APPROVE$/).first()).toBeVisible() }],
  ['confirm fraud on a mule case deny-lists the mule', async p => {
    await nav(p, 'Cases'); await p.locator('select').nth(1).selectOption('MONEY_MULE')
    // Wait for the filtered list: scam-victim cases (correctly) never deny-list anyone.
    await expect(p.locator('tbody tr').first()).toContainText('Money mule', { timeout: 10000 })
    await expect(p.locator('tbody tr').filter({ hasNotText: 'Money mule' })).toHaveCount(0)
    const link = p.locator('tbody tr a').first(); await expect(link).toBeVisible({ timeout: 10000 }); await link.click()
    mule = (await p.locator('div.mt-1.font-mono.text-sm').first().innerText()).trim()
    await p.getByRole('button', { name: /Confirm fraud/ }).click(); await expect(p.getByText('Fraud confirmed').first()).toBeVisible({ timeout: 10000 })
  }],
  ['next payment to the deny-listed mule is blocked', async p => {
    await nav(p, 'Try a payment'); await p.getByRole('button', { name: /Everyday payment/ }).click()
    const payee = p.locator('label', { hasText: 'Payee UPI ID' }).locator('input'); await payee.fill(mule)
    await p.getByRole('button', { name: /^Pay ₹/ }).click(); await expect(p.getByText(/^BLOCK$/).first()).toBeVisible({ timeout: 10000 })
    const pct = Number((await p.locator('span.text-2xl').first().innerText()).replace('%', '')); if (pct < 90) throw new Error(`score only ${pct}%`)
  }],
  ['risk model card', async p => { await nav(p, 'Risk model'); await expect(p.getByText('ROC AUC')).toBeVisible(); await expect(p.getByText('Feature weights')).toBeVisible() }],
  ['knowledge base search', async p => { await nav(p, 'Knowledge base'); await p.getByRole('button', { name: /Ask/ }).click(); await expect(p.getByText(/semantic #/).first()).toBeVisible({ timeout: 15000 }) }],
  ['sign out returns to sign-in', async p => { await p.getByTitle('Sign out').click(); await expect(p).toHaveURL(/\/login$/) }],
]

test('analyst journey', async ({ page }) => {
  const problems: string[] = []
  page.on('pageerror', e => problems.push(`page error: ${e.message}`))
  page.on('response', r => { if (r.status() >= 500) problems.push(`HTTP ${r.status()} ${r.request().method()} ${r.url()}`) })
  for (const [label, fn] of steps) await test.step(label, () => fn(page))
  expect(problems).toEqual([])
})

test.describe('phone width', () => {
  test.use({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })

  test('no page scrolls sideways', async ({ page, request }) => {
    const paths = await (async () => {
  const r = await request.post('/api/auth/login', { data: { username: 'analyst', password: 'fraudshield' } })
  const token = (await r.json()).accessToken
  const cases = await (await request.get('/api/cases?limit=1', { headers: { Authorization: `Bearer ${token}` } })).json()
  await page.addInitScript(t => localStorage.setItem('fraudshield.token', t), token)
  return ['/', '/app', '/app/cases', `/app/cases/${cases[0]?.id ?? ''}`, '/app/playground', '/app/model', '/app/knowledge']
    })()
    for (const path of paths) {
      await page.goto(path)
      await page.waitForTimeout(1500)
      const width = await page.evaluate(() => document.documentElement.scrollWidth)
      expect(width, `${path} is wider than the screen`).toBeLessThanOrEqual(391)
    }
  })
})
