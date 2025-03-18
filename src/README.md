| 包     | 子包   | 类                     | 主要作用                                                                 |
|--------|--------|------------------------|--------------------------------------------------------------------------|
| data   | contact| Notes                  | 便签数据，用于记录便签相关属性和数据                                    |
|        |        | NotesProvider          | 便签数据提供者                                                           |
|        |        | NotesDatabaseHelper    | 数据库帮助类，用于辅助创建、处理数据库的表目                             |
|        |        | MetaData               | 关于同步任务的元数据                                                     |
|        |        | Node                   | 同步任务的管理结点，用于设置、保存同步动作的信息                         |
|        |        | SqlData                | 数据库中基本数据，方法包括读取数据、获取数据中数据、提交数据到数据库    |
|        |        | SqlNode                | 数据库中便签数据，方法包括读取便签内容、从数据库中获取便签数据、设置便签内容、提交便签数据到数据库 |
|        | gtask  | Task                   | 同步任务，将创建、更新和同步动作包装成JSON对象。用本地和远程的JSON对象结点的内容进行设置，获取同步信息，进行本地和远程的同步 |
|        |        | TaskList               | 同步任务列表，将Task组织成同步任务列表进行管理                           |
| exception |       | ActionFailedException  | 动作失败异常                                                             |
|        |        | NetworkFailureException| 网络连接失败                                                             |
| remote |        | GTaskAsyncTask         | GTask异步任务，方法包括任务同步和取消，显示同步任务的进程、通知和结果     |
|        |        | GTaskClient            | GTask客户端，提供选择Google账户、创建任务和任务列表、添加和删除节点、提交、重置更新、获取任务列表功能 |
|        |        | GTaskManager           | GTask管理者，提供同步本地和远端的任务，初始化任务列表、同步内容、文件夹、添加、更新本地和远端节点刷新本地同步任务ID等功能 |
|        |        | GTaskSyncService       | GTask同步服务，用于提供同步服务（开始、取消同步），发送广播              |
| model  |        | Note                   | 单个便签项                                                               |
|        |        | Warning                | 当前活动便签项                                                           |
| tool   |        | BackupUtils            | 备份工具类，用于数据备份读取、显示                                       |
|        |        | DataupUtils            | 便签数据处理工具类，封装如查找、移动、删除数据等操作                     |
|        |        | GTaskStringUtils        | 同步中使用的字符串工具类，为了jsonObject提供string对象                   |
|        |        | ResourceParser         | 界面元素的解析工具类，利用R.java这个类获取资源供程序调用                  |
| ui     |        | AlarmMainActivity      | 报警主界面                                                                |
|        |        | AlarmInitReceiver      | 开机时初始化报警接收器                                                   |
|        |        | AlarmReceiver          | 常规提醒接收器                                                            |
|        |        | DateTimePicker         | 设置提醒时间的控件                                                       |
|        |        | DateTimePickerDialog   | 设置提醒时间的对话界面                                                   |
|        |        | DropdownMenu           | 下拉菜单界面                                                             |
|        |        | FoldersListAdapter     | 文件夹列表适配器（根据数据源渲染）                                       |
|        |        | NoteEditActivity       | 编辑便签的入口                                                           |
|        |        | NoteEditText           | 编辑区的文本编辑界面                                                     |
|        |        | NoteItemData           | 编辑区的数据                                                             |
|        |        | NotesListActivity      | 主界面，用于实现文档文件列表的活动                                       |
|        |        | NotesListAdapter       | 便签列表适配器（按数据源渲染）                                           |
|        |        | NotesPreferenceActivity| 便签同步的设置界面                                                        |
| widget |        | NoteWidgetProvider     | 桌面挂件                                                                 |
|        |        | NoteWidgetProvider_2x  | 2倍大小的桌面挂件                                                        |
|        |        | NoteWidgetProvider_4x  | 4倍大小的桌面挂件                                                        |