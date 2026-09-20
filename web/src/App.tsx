import { FormEvent, useEffect, useMemo, useState } from 'react'
import { Heart, LogOut, MessageCircle, SlidersHorizontal, Sparkles, UserRound, X, ChevronLeft, ChevronRight } from 'lucide-react'

type User = {
  id?: string
  userId?: string
  email?: string
  name?: string
  age?: number
  gender?: string
  photoUri?: string
  secondaryPhotos?: string[]
  country?: string
  state?: string
  religion?: string
  habits?: string
  language?: string
  intentions?: string
}

type AuthResponse = { token: string; user: User }

const API_BASE = (import.meta.env.VITE_API_BASE_URL || 'http://13.232.145.39:4000').replace(/\/$/, '')

async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('tinklet_token')
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })
  if (!response.ok) throw new Error((await response.text()) || `Request failed (${response.status})`)
  return response.json() as Promise<T>
}

function App() {
  const [user, setUser] = useState<User | null>(() => {
    try { return JSON.parse(localStorage.getItem('tinklet_user') || 'null') } catch { return null }
  })
  const [loginError, setLoginError] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [feed, setFeed] = useState<User[]>([])
  const [feedIndex, setFeedIndex] = useState(0)
  const [loading, setLoading] = useState(false)
  const [notice, setNotice] = useState('')
  const [view, setView] = useState<'discover' | 'matches'>('discover')
  const [showFilters, setShowFilters] = useState(false)
  const [countryFilter, setCountryFilter] = useState('')
  const [stateFilter, setStateFilter] = useState('')
  const [genderFilter, setGenderFilter] = useState('')
  const [ageMin, setAgeMin] = useState(18)
  const [ageMax, setAgeMax] = useState(60)

  useEffect(() => {
    if (!user) return
    api<{ feed: User[] }>('/api/swipe/feed')
      .then(data => { setFeed(data.feed || []); setFeedIndex(0) })
      .catch(error => setNotice(error.message))
  }, [user])

  const filteredFeed = useMemo(() => feed.filter(profile => {
    const ageOk = !profile.age || (profile.age >= ageMin && profile.age <= ageMax)
    const countryOk = !countryFilter || (profile.country || '').toLowerCase().includes(countryFilter.toLowerCase())
    const stateOk = !stateFilter || (profile.state || '').toLowerCase().includes(stateFilter.toLowerCase())
    const genderOk = !genderFilter || (profile.gender || '').toLowerCase() === genderFilter.toLowerCase()
    return ageOk && countryOk && stateOk && genderOk
  }), [feed, ageMin, ageMax, countryFilter, stateFilter, genderFilter])

  const current = filteredFeed[feedIndex]

  function moveCard(direction: 1 | -1) {
    if (!current) return
    setFeedIndex(index => Math.max(0, Math.min(index + direction, filteredFeed.length)))
  }

  async function login(event: FormEvent) {
    event.preventDefault()
    setLoginError('')
    setLoading(true)
    try {
      const data = await api<AuthResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email: email.trim(), password })
      })
      localStorage.setItem('tinklet_token', data.token)
      localStorage.setItem('tinklet_user', JSON.stringify(data.user))
      setUser(data.user)
    } catch (error) {
      setLoginError(error instanceof Error ? error.message : 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  function logout() {
    localStorage.removeItem('tinklet_token')
    localStorage.removeItem('tinklet_user')
    setUser(null)
    setFeed([])
    setFeedIndex(0)
  }

  async function swipe(action: 'like' | 'dislike' | 'superlike') {
    if (!current) return
    try {
      const result = await api<{ matched: boolean }>('/api/swipe/action', {
        method: 'POST',
        body: JSON.stringify({ toUserId: current.id || current.userId, action })
      })
      setNotice(result.matched ? 'It’s a match! ❤️' : action === 'like' ? 'Like sent' : action === 'superlike' ? 'Super Like sent' : '')
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Could not update this profile')
    } finally {
      setFeedIndex(index => index + 1)
    }
  }

  if (!user) {
    return (
      <main className="auth-page">
        <section className="auth-card">
          <div className="brand"><span className="brand-heart">♥</span><span>Tinklet</span></div>
          <p className="tagline">Free Dating App</p>
          <h1>Welcome back</h1>
          <p className="muted">Login to continue meeting new people.</p>
          <form onSubmit={login} className="login-form">
            <label>Email<input value={email} onChange={e => setEmail(e.target.value)} type="email" autoComplete="username" placeholder="Enter your email" required /></label>
            <label>Password<input value={password} onChange={e => setPassword(e.target.value)} type="password" autoComplete="current-password" placeholder="Enter your password" required /></label>
            {loginError && <div className="error">{loginError}</div>}
            <button className="primary" disabled={loading}>{loading ? 'Logging in…' : 'Login'}</button>
          </form>
          <div className="signup-note"><Sparkles size={17} /><span>New accounts are created in the Tinklet Android app.</span></div>
          <p className="small">Website login only · No website registration</p>
        </section>
      </main>
    )
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div className="brand"><span className="brand-heart">♥</span><span>Tinklet</span></div>
        <nav>
          <button className={view === 'discover' ? 'nav-active' : ''} onClick={() => setView('discover')}><Sparkles size={18}/>Discover</button>
          <button className={view === 'matches' ? 'nav-active' : ''} onClick={() => setView('matches')}><MessageCircle size={18}/>Matches</button>
        </nav>
        <button className="icon-button" onClick={logout} title="Logout"><LogOut size={19}/></button>
      </header>

      <section className="content">
        <div className="welcome-row">
          <div><p className="eyebrow">Welcome back</p><h1>{user.name || user.email || 'Tinklet member'}</h1></div>
          <button className="filter-button" onClick={() => setShowFilters(true)}><SlidersHorizontal size={18}/> Filters</button>
        </div>

        {view === 'matches' ? (
          <div className="empty-panel"><MessageCircle size={42}/><h2>Your matches</h2><p>Your matched conversations will appear here.</p></div>
        ) : current ? (
          <div className="discovery-area">
            <article className="profile-card">
              <div className="photo-wrap">
                {current.photoUri ? <img src={current.photoUri} alt={current.name || 'Profile'} /> : <div className="photo-placeholder"><UserRound size={76}/></div>}

                <div className="top-gradient" />
                <div className="bottom-gradient" />

                <div className="profile-top">
                  <h2>{current.name || 'Tinklet member'}{current.age ? `, ${current.age}` : ''}</h2>
                  {(current.state || current.country) && <p>📍 {[current.state, current.country].filter(Boolean).join(', ')}</p>}
                </div>

                <div className="online-pill">● Online</div>

                <button className="photo-arrow left" onClick={() => moveCard(-1)} aria-label="Previous profile"><ChevronLeft size={28}/></button>
                <button className="photo-arrow right" onClick={() => moveCard(1)} aria-label="Next profile"><ChevronRight size={28}/></button>

                <div className="profile-actions overlay-actions">
                  <button className="round dislike" onClick={() => swipe('dislike')} aria-label="Dislike"><X size={27}/></button>
                  <button className="round super" onClick={() => swipe('superlike')} aria-label="Super Like"><Sparkles size={25}/></button>
                  <button className="round like" onClick={() => swipe('like')} aria-label="Like"><Heart size={27} fill="currentColor"/></button>
                </div>
              </div>
            </article>
          </div>
        ) : (
          <div className="empty-panel"><Heart size={42}/><h2>No more profiles</h2><p>Check back later for more people.</p></div>
        )}

        {notice && <button className="notice" onClick={() => setNotice('')}>{notice}</button>}
      </section>

      {showFilters && (
        <div className="filter-backdrop" onClick={() => setShowFilters(false)}>
          <section className="filter-modal" onClick={e => e.stopPropagation()}>
            <div className="filter-head">
              <div><p className="eyebrow">Discover</p><h2>Filters</h2></div>
              <button className="icon-button" onClick={() => setShowFilters(false)}><X size={20}/></button>
            </div>

            <label>Country<input value={countryFilter} onChange={e => setCountryFilter(e.target.value)} placeholder="Any country" /></label>
            <label>State<input value={stateFilter} onChange={e => setStateFilter(e.target.value)} placeholder="Any state" /></label>

            <label>Gender
              <select value={genderFilter} onChange={e => setGenderFilter(e.target.value)}>
                <option value="">Any gender</option>
                <option value="Male">Male</option>
                <option value="Female">Female</option>
                <option value="Neutral">Neutral</option>
              </select>
            </label>

            <div className="age-row">
              <label>Min age<input type="number" min="18" max={ageMax} value={ageMin} onChange={e => setAgeMin(Number(e.target.value))} /></label>
              <label>Max age<input type="number" min={ageMin} max="100" value={ageMax} onChange={e => setAgeMax(Number(e.target.value))} /></label>
            </div>

            <button className="primary" onClick={() => { setFeedIndex(0); setShowFilters(false) }}>Apply Filters</button>
          </section>
        </div>
      )}
    </main>
  )
}

export default App
