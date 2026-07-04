# OA 审批模块方案

## 1. 背景与目标

腾飞企业管理系统 V1 的管理后台已初具规模。下一步需要向**企业日常业务**方向延伸，审批流是覆盖面最广、感知最强的切入点。

**目标**：提供一个轻量、可配置、与现有组织架构深度联动的审批引擎，支持请假、报销、采购等常见表单的在线审批。

## 2. 技术方案选型

### 2.1 三种方案对比

| 方案 | 实现方式 | 开发周期 | 灵活性 | 推荐度 |
|------|----------|----------|--------|--------|
| 硬编码审批链 | 每个表单类型写死审批顺序 | 2-3 天 | 差 | ❌ |
| JSON 配置审批节点 | `config_json` 存审批流程定义，运行时解析 | 1-2 周 | 良 | ✅ 本期采用 |
| 完整流程引擎 | 引入 Activiti/Camunda | 1-2 月 | 优 | ❌ 过重 |

### 2.2 采用方案：JSON 配置 + 条件表达式

- 管理员在后台为每种表单类型配置审批流程（JSON）
- 引擎运行时解析 JSON，动态确定审批人和节点
- 支持条件分支（如"金额 > 5000 走总经理"）
- 后续可扩展并行审批、子流程

## 3. 表结构设计

共 4 张核心表，均继承 BaseEntity（id, createdAt, updatedAt, isDeleted）。

### 3.1 流程定义表 `wf_definition`

流程的"模板"，定义每种表单类型的审批链。

| 字段 | 类型 | 说明 |
|------|------|------|
| tenant_id | BIGINT | 租户隔离 |
| form_type | VARCHAR(64) | 表单类型：leave, expense, purchase |
| form_name | VARCHAR(128) | 表单名称：请假申请 |
| process_key | VARCHAR(128) | 流程标识：LEAVE_APPROVAL |
| config_json | TEXT | 审批节点 JSON 配置 |
| version | INT | 版本号（每次修改递增） |
| status | TINYINT | 1=启用 0=停用 |
| is_default | TINYINT | 是否默认配置 |

### 3.2 流程实例表 `wf_instance`

每次发起审批产生一条实例记录。

| 字段 | 类型 | 说明 |
|------|------|------|
| tenant_id | BIGINT | 租户隔离 |
| instance_no | VARCHAR(64) | 实例编号：WF202607030001 |
| definition_id | BIGINT | 关联流程定义 |
| form_type | VARCHAR(64) | 表单类型 |
| biz_type | VARCHAR(64) | 业务类型：leave, expense |
| biz_id | BIGINT | 业务表主键 ID |
| biz_table | VARCHAR(128) | 业务表名 |
| applicant_id | BIGINT | 申请人 ID |
| applicant_name | VARCHAR(64) | 申请人姓名 |
| status | VARCHAR(32) | PENDING/APPROVED/REJECTED/CANCELED |
| current_node | VARCHAR(64) | 当前待办节点 key |
| priority | TINYINT | 0=普通 1=紧急 2=特急 |

### 3.3 审批节点表 `wf_node`

记录每个审批节点的处理情况。

| 字段 | 类型 | 说明 |
|------|------|------|
| instance_id | BIGINT | 关联流程实例 |
| node_key | VARCHAR(64) | 节点 key |
| node_name | VARCHAR(128) | 节点名称 |
| approver_type | VARCHAR(32) | 审批人类型 |
| approver_id | BIGINT | 审批人 ID |
| approver_name | VARCHAR(64) | 审批人姓名 |
| resolve_mode | VARCHAR(32) | FIRST/ALL/ANYONE |
| status | VARCHAR(32) | WAITING/APPROVING/APPROVED/REJECTED/CANCELED |
| result | VARCHAR(32) | 审批结果 |
| comment | TEXT | 审批意见 |
| action_by | BIGINT | 操作人 ID |
| action_at | DATETIME | 操作时间 |
| due_at | DATETIME | 截止时间 |

### 3.4 审批记录表 `wf_record`

每次审批操作的完整审计记录。

| 字段 | 类型 | 说明 |
|------|------|------|
| instance_id | BIGINT | 关联流程实例 |
| node_id | BIGINT | 关联节点 |
| operator_id | BIGINT | 操作人 ID |
| operator_name | VARCHAR(64) | 操作人姓名 |
| action | VARCHAR(32) | APPROVE/REJECT/TRANSFER/AGENT/RETURN/CANCEL |
| comment | TEXT | 审批意见 |
| before_status | VARCHAR(32) | 操作前实例状态 |
| after_status | VARCHAR(32) | 操作后实例状态 |
| target_user_id | BIGINT | 转发/代理目标人 |

## 4. config_json 审批节点配置

审批流程的核心，存储在 `wf_definition.config_json` 中：

```json
{
  "nodes": [
    {
      "key": "node_leader",
      "name": "直属上级审批",
      "approverType": "LEADER",
      "resolveMode": "FIRST",
      "orderBy": 1,
      "condition": null
    },
    {
      "key": "node_dept_leader",
      "name": "部门负责人审批",
      "approverType": "DEPT_LEADER",
      "resolveMode": "FIRST",
      "orderBy": 2,
      "condition": null
    },
    {
      "key": "node_hr_archive",
      "name": "HR备案",
      "approverType": "ROLE",
      "resolveMode": "FIRST",
      "orderBy": 3,
      "condition": null,
      "targetRoleId": 5
    }
  ]
}
```

### 4.1 approverType 枚举

| 值 | 含义 | 解析逻辑 |
|----|------|----------|
| `LEADER` | 直属上级 | 查 User.leaderId |
| `DEPT_LEADER` | 部门负责人 | 查用户所属部门的 Dept.leaderId |
| `SPECIFIC_USER` | 指定用户 | config_json 中的 targetUserId |
| `ROLE` | 角色 | config_json 中的 targetRoleId，取其下所有用户 |
| `SELF_APPROVE` | 自审批 | 自动通过（用于备案类流程） |

### 4.2 resolveMode 会签模式

| 值 | 含义 |
|----|------|
| `FIRST` | 单人审批，一人通过/驳回即可 |
| `ALL` | 会签，所有人必须全部同意 |
| `ANYONE` | 或签，任选一人审批即可 |

### 4.3 condition 条件表达式

当前支持简单的 JSON 路径条件，例如：
- `form.amount > 1000` — 报销金额超过 1000 才进入该节点
- `form.days >= 3` — 请假天数 ≥ 3 天才需要分管领导审批

后续可扩展为 Aviator/MVEL 表达式引擎。

## 5. 权限编码

按照 `PERM_{模块}:{操作}` 格式：

| 权限码 | 说明 |
|--------|------|
| `PERM_approval:view` | 查看审批 |
| `PERM_approval:apply` | 发起审批 |
| `PERM_approval:approve` | 审批通过 |
| `PERM_approval:reject` | 审批驳回 |
| `PERM_approval:transfer` | 审批转交 |
| `PERM_approval:cancel` | 审批撤回 |
| `PERM_approval:delegate` | 审批代理 |
| `PERM_approval:manage` | 流程管理（管理员） |

## 6. 后端模块设计

### 6.1 包结构

建议在 `core-org` 模块下新增 `approval` 子包（不新建 Maven 模块，保持轻量）：

```
core-org/
└── src/main/java/com/tengyei/org/
    ├── controller/
    │   ├── ApprovalFlowController.java      -- 管理员：流程配置 CRUD
    │   ├── ApprovalInstanceController.java  -- 通用：发起/查询/推进
    │   ├── ApprovalTodoController.java      -- 我的待办/已办/抄送
    │   └── ApprovalStatisticsController.java-- 统计报表
    ├── dto/
    │   ├── ApprovalApplyDTO.java
    │   ├── ApprovalApproveDTO.java
    │   ├── ApprovalFlowConfigDTO.java
    │   └── ...
    ├── entity/
    │   ├── WfDefinition.java
    │   ├── WfInstance.java
    │   ├── WfNode.java
    │   └── WfRecord.java
    └── service/
        ├── ApprovalFlowService.java         -- 流程配置管理
        ├── ApprovalEngineService.java       -- 核心引擎
        └── ApprovalResolverService.java     -- 审批人解析
```

### 6.2 核心引擎伪代码

#### 发起审批

```
ApprovalEngineService.apply(formType, bizType, bizId, formData, applicantId) {
  1. 查询 wf_definition (tenant_id, form_type) 获取 config_json
  2. 解析 JSON nodes 数组
  3. 对每个 node：
     a. 求值 condition（null 则无条件通过）
     b. 调用 Resolver 确定审批人
     c. 写入 wf_node 记录，status=WAITING
  4. 写入 wf_instance，status=PENDING
  5. 写入 wf_record（action=APPLY）
}
```

#### 审批操作

```
ApprovalEngineService.approve(instanceId, action, comment, operatorId) {
  1. 查询 wf_instance 获取实例信息
  2. 获取当前节点 wf_node (status=WAITING 且 approver_id=operatorId)
  3. 写入 wf_record
  4. 更新 wf_node.status/result/comment/action_at
  5. 如果 action=REJECT：
     a. wf_instance.status = REJECTED
     b. 将所有 WAITING 后续节点设为 CANCELED
  6. 如果 action=APPROVE：
     a. 查找下一节点
     b. 有下一节点：current_node = 下一节点 key
     c. 无下一节点：wf_instance.status = APPROVED → 触发业务回调
}
```

## 7. 前端页面设计

### 7.1 页面清单

| 页面 | 路由 | 权限要求 | 说明 |
|------|------|----------|------|
| 我的待办 | `/company/approval/todo` | 所有用户 | 显示当前有待处理审批的任务 |
| 我已发起 | `/company/approval/my` | 所有用户 | 显示自己发起的审批实例 |
| 我已审批 | `/company/approval/done` | 所有用户 | 显示自己已处理的记录 |
| 审批详情 | `/company/approval/:id` | 相关人员 | 查看实例详情、审批链、表单数据 |
| 流程管理 | `/company/approval/flows` | 管理员 | 管理审批流程模板配置 |
| 发起审批 | （弹窗或路由） | 所有用户 | 选择表单类型 → 填写表单 → 发起 |

### 7.2 布局示意

**我的待办页**：
```
┌─────────────────────────────────────────────┐
│  我的待办                                    │
├──────────┬──────────────────────────────────┤
│ 筛选条件  │  待办任务列表（表格）              │
│ form_type │  ┌────────┬───────┬──────┬────┐ │
│ date_range│  │ 申请单  │申请人 │申请时间│操作│ │
│           │  ├────────┼───────┼──────┼────┤ │
│           │  │ 请假申请 │张三   │07-03 │审批│ │
│           │  │ 报销申请 │李四   │07-03 │审批│ │
│           │  └────────┴───────┴──────┴────┘ │
│ 分页      │                                  │
└──────────┴──────────────────────────────────┘
```

**审批弹窗**：
```
┌─────────────────────────────────────┐
│ 审批：请假申请 WF202607030001         │
├─────────────────────────────────────┤
│ 申请人：张三    申请时间：2026-07-03  │
│                                     │
│ 【表单数据】                         │
│ 请假类型：年假    天数：2天           │
│ 起止时间：2026-07-05 至 2026-07-06   │
│ 事由：回家探亲                        │
│                                     │
│ 【审批记录】                         │
│ ● 直属上级 李四  已通过  07-03 10:00 │
│ ○ 部门负责人 王五  待审批             │
│                                     │
│ 【审批意见】                         │
│ [ textarea ]                         │
│                                     │
│        [ 驳回 ]   [ 通过 ]            │
└─────────────────────────────────────┘
```

## 8. Flyway Migration 脚本

新增 `V5__approval_workflow.sql`，包含：
- 4 张核心表建表语句
- 初始数据：系统默认角色/权限点

## 9. 实施计划

### Phase 1：核心审批引擎（1.5 周）

| 天 | 工作内容 |
|----|----------|
| 1-2 | 数据库表设计 + Flyway migration |
| 3-4 | 后端 Entity + Mapper + Entity 设计 |
| 5-6 | ApprovalEngineService + ResolverService |
| 7-8 | 后端 Controller + DTO/VO |
| 9-10 | 前端：我的待办 + 审批弹窗 |
| 11 | 前端：我已发起 + 审批详情 |
| 12-13 | 联调 + 测试 + 修复 |

### Phase 2：流程管理（1 周）

| 天 | 工作内容 |
|----|----------|
| 1-2 | 流程模板配置页（可视化编辑审批链） |
| 3-4 | 条件表达式支持 + 会签/或签 |
| 5 | 联调测试 |

### Phase 3：统计与扩展（按需）

| 天 | 工作内容 |
|----|----------|
| 1-2 | 审批统计报表（平均审批时长、驳回率等） |
| 3 | 审批转交/代理 |
| 4-5 | 超时提醒（Redis + 定时任务） |

## 10. 技术风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| 条件表达式求值 | 初期需灵活配置审批链 | 先用 JSON 路径简单条件，后续引入 Aviator 引擎 |
| 审批人动态变化 | 员工离职/调岗后审批链断裂 | 记录审批时点快照（approver_name 存入 wf_node） |
| 并发审批 | 多人同时操作同一实例 | wf_node 加乐观锁（version 字段） |
| 性能 | 大量审批实例 | wf_instance.status + current_node 加索引；Redis 缓存流程定义 |

## 11. 与现有功能的联动

| 联动点 | 方案 |
|--------|------|
| 组织架构 | 审批人解析直接复用 User.dept_id、Dept.leader_id |
| RBAC 权限 | 权限点加入 `PERM_approval:*` 系列 |
| 审计日志 | 审批操作同样加 `@Auditable` 注解 |
| 数据权限 | 非管理员只能看到自己部门的审批（data_scope 过滤） |
| 多租户 | 所有表自带 tenant_id，MP 租户插件自动隔离 |
