import { useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'motion/react'
import { ArrowRight, ArrowUpRight, Check, Copy, FileSearch, Gauge, Network, Scale, ShieldCheck, Workflow } from 'lucide-react'
import { Logo } from '../components/ui'
import ScoringFeed from './ScoringFeed'
import MuleRing from './MuleRing'
import Architecture from './Architecture'
import { CountUp, Reveal } from './motion'

// Where the code lives. Override at build time with VITE_REPO_URL.
const REPO: string = import.meta.env.VITE_REPO_URL ?? 'https://github.com/swayam555/fraudshield'

export default function Landing() {
  return (
    <div className="overflow-x-clip">
      <Nav />
      <Hero />
      <Numbers />
      <Problem />
      <Pipeline />
      <Mules />
      <Explain />
      <Copilot />
      <ArchitectureSection />
      <RunIt />
      <Footer />
    </div>
  )
}

function Nav() {
  return (
    <header className="sticky top-0 z-30 border-b border-line/80 bg-bg/75 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5">
        <Link to="/" className="focus-ring rounded-md"><Logo /></Link>
        <nav className="hidden items-center gap-7 text-sm text-muted md:flex">
          <a href="#pipeline" className="hover:text-fg">Scoring</a>
          <a href="#mules" className="hover:text-fg">Mule rings</a>
          <a href="#copilot" className="hover:text-fg">Copilot</a>
          <a href="#architecture" className="hover:text-fg">Architecture</a>
        </nav>
        <Link to="/login" className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-signal px-3.5 text-sm font-medium text-[#06101f] hover:bg-[#7aaeff]">
          Open the console <ArrowRight className="h-4 w-4" />
        </Link>
      </div>
    </header>
  )
}

function Hero() {
  return (
    <section className="relative">
      <div className="pointer-events-none absolute inset-0 -z-10 bg-[radial-gradient(ellipse_at_70%_0%,rgb(91_155_255/0.14),transparent_55%)]" />
      <div className="pointer-events-none absolute inset-0 -z-10 bg-[linear-gradient(to_right,#151a20_1px,transparent_1px),linear-gradient(to_bottom,#151a20_1px,transparent_1px)] bg-[size:64px_64px] [mask-image:radial-gradient(ellipse_at_top,black_25%,transparent_70%)]" />
      <div className="mx-auto grid max-w-6xl items-center gap-14 px-5 pb-24 pt-16 lg:grid-cols-[1.05fr_1fr] lg:pt-24">
        <div>
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
            className="inline-flex items-center gap-2 rounded-full border border-line-2 bg-surface px-3 py-1 font-mono text-[11px] uppercase tracking-[0.14em] text-fg-2">
            <span className="h-1.5 w-1.5 rounded-full bg-approve" /> UPI fraud & money-mule detection
          </motion.div>
          <motion.h1 initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.08, duration: 0.6 }}
            className="mt-6 text-[44px] font-semibold leading-[1.02] tracking-[-0.03em] text-fg sm:text-6xl lg:text-[64px]">
            Stop fraud in the <span className="text-signal">milliseconds</span> before money moves.
          </motion.h1>
          <motion.p initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.16, duration: 0.6 }}
            className="mt-6 max-w-xl text-lg leading-relaxed text-fg-2">
            FraudShield scores every UPI payment with plain-English reasons, finds the mule networks that move stolen money,
            and gives investigators an AI copilot that drafts cited case briefs.
          </motion.p>
          <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.24, duration: 0.6 }} className="mt-9 flex flex-wrap gap-3">
            <Link to="/login" className="inline-flex h-12 items-center gap-2 rounded-xl bg-signal px-5 text-[15px] font-medium text-[#06101f] hover:bg-[#7aaeff]">
              Open the live console <ArrowRight className="h-4 w-4" />
            </Link>
            <a href={REPO} className="inline-flex h-12 items-center gap-2 rounded-xl border border-line-2 bg-surface px-5 text-[15px] font-medium text-fg hover:bg-raised">
              View the source <ArrowUpRight className="h-4 w-4" />
            </a>
          </motion.div>
        </div>
        <motion.div initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.25, duration: 0.8 }}>
          <ScoringFeed rows={6} />
        </motion.div>
      </div>
    </section>
  )
}

function Numbers() {
  const items = [
    { v: <><CountUp to={99.7} decimals={1} />%</>, l: 'of genuine payments approved instantly' },
    { v: <>~<CountUp to={5} /> ms</>, l: 'to score a payment, with its reasons' },
    { v: <><CountUp to={0.99} decimals={2} />+</>, l: 'ROC AUC after automatic retraining' },
    { v: <>&lt;<CountUp to={1} /> s</>, l: 'from an analyst verdict to a deny-list' },
  ]
  return (
    <section className="border-y border-line bg-[#0d1013]">
      <div className="mx-auto grid max-w-6xl grid-cols-2 gap-y-8 px-5 py-10 md:grid-cols-4">
        {items.map((it, i) => (
          <Reveal key={i} delay={i * 0.06} className="md:border-l md:border-line md:pl-6 md:first:border-0 md:first:pl-0">
            <div className="text-[34px] font-semibold tracking-tight text-fg">{it.v}</div>
            <p className="mt-1 max-w-[15rem] text-sm leading-snug text-muted">{it.l}</p>
          </Reveal>
        ))}
      </div>
    </section>
  )
}

function SectionHead({ eyebrow, title, children }: { eyebrow: string; title: string; children?: React.ReactNode }) {
  return (
    <Reveal className="max-w-2xl">
      <div className="font-mono text-[11px] uppercase tracking-[0.16em] text-signal">{eyebrow}</div>
      <h2 className="mt-3 text-3xl font-semibold leading-[1.1] tracking-[-0.025em] text-fg sm:text-[42px]">{title}</h2>
      {children && <p className="mt-4 text-[17px] leading-relaxed text-fg-2">{children}</p>}
    </Reveal>
  )
}

function Problem() {
  const facts = [
    { v: '₹2,055 cr', l: 'lost to cyber fraud in FY24, up from ₹421 crore a year earlier (RBI)' },
    { v: '8.5 lakh', l: 'suspected mule accounts reportedly found by the CBI\'s Operation Chakra-V' },
    { v: '7 days', l: 'to file a Suspicious Transaction Report with FIU-IND' },
  ]
  return (
    <section className="mx-auto max-w-6xl px-5 py-24">
      <SectionHead eyebrow="The problem" title="New scams beat old rules.">
        In a "digital arrest" scam the victim pays from their own phone, in their own city. Device and location checks
        pass. Fraud has to be caught from behaviour, from the network of accounts, and fast enough not to slow anyone down.
      </SectionHead>
      <div className="mt-12 grid gap-px overflow-hidden rounded-xl border border-line bg-line md:grid-cols-3">
        {facts.map((f, i) => (
          <Reveal key={f.v} delay={i * 0.08} className="bg-surface p-6">
            <div className="text-3xl font-semibold tracking-tight text-fg">{f.v}</div>
            <p className="mt-2 text-sm leading-relaxed text-muted">{f.l}</p>
          </Reveal>
        ))}
      </div>
    </section>
  )
}

function Pipeline() {
  const steps = [
    { t: '0 ms', title: 'Payment arrives', body: 'JWT checked and rate-limited at the gateway. An idempotency key stops a retried request from paying twice.' },
    { t: '~1 ms', title: '20 signals in one round trip', body: 'A single Redis Lua script reads velocity, usual amount, devices, payees and location, and records the payment, atomically.' },
    { t: '~2 ms', title: 'Scored and explained', body: 'A logistic model turns 17 features into a probability. Each feature\'s contribution is the explanation.' },
    { t: '~5 ms', title: 'Decided', body: 'Approve, hold for review or block. Committed with an outbox row, so the Kafka event can never be lost.' },
  ]
  return (
    <section id="pipeline" className="scroll-mt-16 border-y border-line bg-[#0d1013]">
      <div className="mx-auto max-w-6xl px-5 py-24">
        <SectionHead eyebrow="The scoring path" title="Five milliseconds, four steps.">
          The payment waits for the risk engine, but never longer than 300 ms. If the engine is slow, a circuit breaker
          opens: small payments go through, large ones are held, and both are marked degraded.
        </SectionHead>
        <div className="relative mt-14">
          <motion.div className="absolute left-0 right-0 top-[11px] hidden h-px origin-left bg-gradient-to-r from-signal via-signal/50 to-transparent md:block"
            initial={{ scaleX: 0 }} whileInView={{ scaleX: 1 }} viewport={{ once: true }} transition={{ duration: 1.4, ease: 'easeOut' }} />
          <ol className="grid gap-8 md:grid-cols-4">
            {steps.map((s, i) => (
              <Reveal key={s.title} delay={0.2 + i * 0.15}>
                <li className="relative list-none">
                  <span className="relative z-10 flex h-[23px] w-[23px] items-center justify-center rounded-full border border-signal/60 bg-bg">
                    <span className="h-2 w-2 rounded-full bg-signal" />
                  </span>
                  <div className="mt-5 font-mono text-xs text-signal">{s.t}</div>
                  <h3 className="mt-1 text-lg font-semibold tracking-tight text-fg">{s.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-fg-2">{s.body}</p>
                </li>
              </Reveal>
            ))}
          </ol>
        </div>
      </div>
    </section>
  )
}

function Mules() {
  return (
    <section id="mules" className="mx-auto max-w-6xl scroll-mt-16 px-5 py-24">
      <div className="grid items-center gap-14 lg:grid-cols-[0.9fr_1.1fr]">
        <div>
          <SectionHead eyebrow="Mule rings" title="One payment looks harmless. The network doesn't.">
            Mules are new accounts that receive stolen money and pass it on within minutes. The graph analyser watches
            every payment over Kafka, scores every account, and puts mules on a watch-list so the next payment into them is held.
          </SectionHead>
          <Reveal delay={0.15}>
            <p className="mt-6 text-sm leading-relaxed text-muted">
              It is merchant-aware: a chai shop with hundreds of payers is normal. Fan-in only counts when the account also
              behaves like a conduit, which cut false positives from about 3% to 0.2% in testing.
            </p>
          </Reveal>
        </div>
        <Reveal delay={0.1}><MuleRing /></Reveal>
      </div>
    </section>
  )
}

function Explain() {
  const reasons = [
    { l: 'Amount far above this payer\'s usual spend', v: 3.1 }, { l: 'Payment from a device never seen', v: 2.4 },
    { l: 'Location jump faster than possible', v: 2.2 }, { l: 'First payment to this payee', v: 1.3 }, { l: 'Payee account is a day old', v: 0.9 },
  ]
  return (
    <section className="border-y border-line bg-[#0d1013]">
      <div className="mx-auto grid max-w-6xl items-center gap-14 px-5 py-24 lg:grid-cols-2">
        <Reveal className="order-2 rounded-xl border border-line bg-surface p-6 lg:order-1">
          <div className="flex items-center justify-between">
            <div>
              <div className="font-mono text-xs text-muted">divya.pillai17@okicici → fastpay83130@ybl</div>
              <div className="num mt-1 text-2xl font-semibold text-fg">₹74,000</div>
            </div>
            <span className="rounded-md bg-block/10 px-2 py-1 font-mono text-xs font-medium text-block ring-1 ring-inset ring-block/30">BLOCK · 0.97</span>
          </div>
          <div className="mt-6 space-y-3">
            {reasons.map((r, i) => (
              <div key={r.l}>
                <div className="flex justify-between text-sm"><span className="text-fg-2">{r.l}</span><span className="num font-mono text-xs text-muted">+{r.v.toFixed(1)}</span></div>
                <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-white/[0.06]">
                  <motion.div className="h-full rounded-full" style={{ background: r.v > 2 ? '#ff6363' : r.v > 1 ? '#f5b03d' : '#565e69' }}
                    initial={{ width: 0 }} whileInView={{ width: `${(r.v / 3.2) * 100}%` }} viewport={{ once: true }} transition={{ duration: 0.8, delay: 0.2 + i * 0.1 }} />
                </div>
              </div>
            ))}
          </div>
        </Reveal>
        <div className="order-1 lg:order-2">
          <SectionHead eyebrow="Explainable by design" title="Every decision says why.">
            No black box and no separate explainer: in a logistic model each feature's contribution is weight × value. The
            same reasons go to the customer, the analyst and the regulator.
          </SectionHead>
          <Reveal delay={0.15}>
            <ul className="mt-8 space-y-3 text-[15px] text-fg-2">
              {['Analyst verdicts become labelled training data', 'Retrained in the background; promoted only if it beats the current model', 'Thresholds can be tightened live during a scam wave'].map(t => (
                <li key={t} className="flex gap-3"><Check className="mt-0.5 h-5 w-5 shrink-0 text-signal" />{t}</li>
              ))}
            </ul>
          </Reveal>
        </div>
      </div>
    </section>
  )
}

function Copilot() {
  const features = [
    { icon: FileSearch, t: 'Hybrid retrieval', b: 'pgvector and Postgres full-text, fused with Reciprocal Rank Fusion. Finds paraphrases and exact terms like "STR" or "1930".' },
    { icon: ShieldCheck, t: 'Cited, every time', b: 'Briefs and answers carry numbered sources from RBI, FIU-IND and NPCI guidance and internal playbooks.' },
    { icon: Workflow, t: 'Works without an API key', b: 'Embeddings run in-process. Without an LLM, a deterministic writer still produces a cited brief.' },
  ]
  return (
    <section id="copilot" className="mx-auto max-w-6xl scroll-mt-16 px-5 py-24">
      <SectionHead eyebrow="Investigator copilot" title="A first draft of every case brief, with sources.">
        One case per suspicious account, a map of where the money went, and an assistant that answers "do we need to
        file an STR, and by when?" from the actual guidance.
      </SectionHead>
      <div className="mt-12 grid gap-5 md:grid-cols-3">
        {features.map((f, i) => (
          <Reveal key={f.t} delay={i * 0.08} className="rounded-xl border border-line bg-surface p-6 transition hover:border-line-2">
            <f.icon className="h-5 w-5 text-signal" strokeWidth={1.75} />
            <h3 className="mt-4 font-semibold text-fg">{f.t}</h3>
            <p className="mt-1.5 text-sm leading-relaxed text-fg-2">{f.b}</p>
          </Reveal>
        ))}
      </div>
    </section>
  )
}

function ArchitectureSection() {
  return (
    <section id="architecture" className="scroll-mt-16 border-t border-line bg-[#0d1013]">
      <div className="mx-auto max-w-6xl px-5 py-24">
        <SectionHead eyebrow="Architecture" title="Synchronous where it must be, event-driven everywhere else.">
          The payment path is a direct, time-boxed call. Everything that can happen after the decision (graph analysis,
          cases, the live feed, retraining) flows over Kafka.
        </SectionHead>
        <Reveal delay={0.1} className="mt-12 rounded-xl border border-line bg-surface p-6"><Architecture /></Reveal>
        <Reveal delay={0.15} className="mt-8 flex flex-wrap gap-2">
          {['Java 21', 'Spring Boot 4', 'Spring Cloud Gateway', 'Resilience4j', 'Kafka', 'Redis + Lua', 'PostgreSQL + pgvector', 'LangChain4j', 'React 19', 'Kubernetes'].map(t => (
            <span key={t} className="rounded-md border border-line-2 px-2.5 py-1 font-mono text-xs text-fg-2">{t}</span>
          ))}
        </Reveal>
        <Reveal delay={0.2} className="mt-12 grid gap-5 md:grid-cols-3">
          {[{ icon: Gauge, t: 'Circuit breaker', b: '300 ms budget; fail open for small payments, closed for large ones.' },
            { icon: Network, t: 'Transactional outbox', b: 'Payment and event commit together; consumers de-duplicate.' },
            { icon: Scale, t: 'Champion / challenger', b: 'A new model must match AUC without raising false positives.' }].map(f => (
            <div key={f.t} className="flex gap-3">
              <f.icon className="mt-0.5 h-5 w-5 shrink-0 text-signal" strokeWidth={1.75} />
              <div><div className="font-medium text-fg">{f.t}</div><div className="mt-0.5 text-sm text-muted">{f.b}</div></div>
            </div>
          ))}
        </Reveal>
      </div>
    </section>
  )
}

function RunIt() {
  const cmd = 'git clone ' + REPO + '.git\ncd fraudshield\ndocker compose up --build\n# open http://localhost:3001'
  const [copied, setCopied] = useState(false)
  const copy = () => { navigator.clipboard?.writeText(cmd).then(() => { setCopied(true); setTimeout(() => setCopied(false), 1500) }) }
  return (
    <section className="px-5 py-24">
      <Reveal className="mx-auto grid max-w-6xl items-center gap-10 overflow-hidden rounded-2xl border border-line-2 bg-[radial-gradient(ellipse_at_top_left,rgb(91_155_255/0.15),transparent_60%)] bg-surface p-8 sm:p-12 lg:grid-cols-2">
        <div>
          <h2 className="text-3xl font-semibold tracking-[-0.025em] text-fg sm:text-[40px]">Run the whole bank-side stack locally.</h2>
          <p className="mt-4 text-[17px] leading-relaxed text-fg-2">One command starts every service with Kafka, Redis and Postgres. Press <b className="text-fg">Start traffic</b>, then launch an attack and open the case it creates.</p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link to="/login" className="inline-flex h-11 items-center gap-2 rounded-xl bg-signal px-5 text-sm font-medium text-[#06101f] hover:bg-[#7aaeff]">Open the console <ArrowRight className="h-4 w-4" /></Link>
            <a href={REPO} className="inline-flex h-11 items-center gap-2 rounded-xl border border-line-2 px-5 text-sm font-medium text-fg hover:bg-raised">Source <ArrowUpRight className="h-4 w-4" /></a>
          </div>
        </div>
        <div className="relative rounded-xl border border-line bg-bg p-5 font-mono text-[13px] leading-7 text-fg-2">
          <button onClick={copy} aria-label="Copy commands" className="absolute right-3 top-3 rounded-md p-2 text-muted hover:bg-white/5 hover:text-fg">
            {copied ? <Check className="h-4 w-4 text-approve" /> : <Copy className="h-4 w-4" />}
          </button>
          {cmd.split('\n').map(l => <div key={l} className={l.startsWith('#') ? 'text-faint' : ''}>{l.startsWith('#') ? l : <><span className="text-signal">$</span> {l}</>}</div>)}
        </div>
      </Reveal>
    </section>
  )
}

function Footer() {
  return (
    <footer className="border-t border-line">
      <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-4 px-5 py-8 text-sm text-muted sm:flex-row sm:items-center">
        <div className="flex items-center gap-3"><Logo /><span>A portfolio project. All customers and payments are synthetic.</span></div>
        <div className="flex gap-5"><Link to="/login" className="hover:text-fg">Console</Link><a href={REPO} className="hover:text-fg">Source</a></div>
      </div>
    </footer>
  )
}
