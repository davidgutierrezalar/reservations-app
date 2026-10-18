import type { Page } from './api'

export default function Pager({ page, onChange }: { page: Page<unknown>['page']; onChange: (n: number) => void }) {
  if (page.totalPages <= 1) return null
  return (
    <div className="pager">
      <button disabled={page.number === 0} onClick={() => onChange(page.number - 1)}>
        Previous
      </button>
      <span>
        Page {page.number + 1} of {page.totalPages} ({page.totalElements} total)
      </span>
      <button disabled={page.number + 1 >= page.totalPages} onClick={() => onChange(page.number + 1)}>
        Next
      </button>
    </div>
  )
}
