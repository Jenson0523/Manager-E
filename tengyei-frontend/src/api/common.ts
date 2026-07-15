import request from './request'
import type { DeptTreeVO } from '@/types/org'

export interface CommonOptionsVO {
  /** 本租户在职用户(仅 id+姓名) */
  users: { id: number; realName: string }[]
  /** 本租户启用角色(仅 id+名称) */
  roles: { id: number; name: string }[]
  /** 部门树 */
  depts: DeptTreeVO[]
  /** 企业列表(仅平台账号返回) */
  companies?: { id: number; name: string }[]
}

/**
 * 全站选人/选角色/选部门下拉(登录即可,不要求管理权限)。
 * 审批抄送/转交/加签、部门负责人、通知受众等业务选择场景统一走这里,
 * 避免借用 user:view/role:view/dept:view 门控的管理接口导致窄权限账号误报无权限。
 */
export const commonApi = {
  options: () => request.get<never, CommonOptionsVO>('/v1/common/options'),
}
