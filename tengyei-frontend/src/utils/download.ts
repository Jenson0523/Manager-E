import request from '@/api/request'
import type { AxiosResponse } from 'axios'

/**
 * Downloads an Excel file via axios blob request.
 * The request.ts interceptor returns the full AxiosResponse for non-JSON responses,
 * so we cast to AxiosResponse<Blob> to access .data.
 */
export async function downloadExcel(
  url: string,
  params: Record<string, unknown>,
  filename: string
): Promise<void> {
  const res = (await request.get(url, {
    params,
    responseType: 'blob',
  })) as unknown as AxiosResponse<Blob>
  const blob = new Blob([res.data], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  })
  const href = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = href
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(href)
}
