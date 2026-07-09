/** 权限模块 slug -> 中文分组名(权限配置页展示用);未收录的回退显示原始 slug */
export const MODULE_LABELS: Record<string, string> = {
  company: '企业信息',
  dept: '部门管理',
  branch: '分支机构',
  user: '人员管理',
  role: '角色权限',
  log: '日志',
  setting: '系统设置',
  approval: '审批中心',
  announcement: '通知管理',
  platform: '平台管理',
  module: '模块管理',
}

export function moduleLabel(slug: string): string {
  return MODULE_LABELS[slug] ?? slug
}
