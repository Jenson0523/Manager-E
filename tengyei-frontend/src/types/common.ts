export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface IdResult {
  id: number
}
