# Spring Boot 经典包结构

> 说明：本文是 Spring Boot 通用包结构参考，不是当前 mdreader 后端的逐文件清单。当前项目实际结构、分层约定与业务规则见 [`backend/AGENTS.md`](../../backend/AGENTS.md)。

```text
src/main/java/com/example/project
 ├── ProjectApplication.java           # 启动类（位于根包下）
 │
 ├── config                            # 配置类包
 │    ├── CorsConfig.java              # 跨域配置
 │    ├── RedisConfig.java             # Redis配置
 │    ├── WebMvcConfig.java            # 拦截器/静态资源等Web配置
 │    └── MybatisPlusConfig.java       # 持久层框架配置
 │
 ├── controller                        # 控制层（API入口）
 │    ├── UserController.java
 │    └── OrderController.java
 │
 ├── service                           # 业务逻辑层
 │    ├── UserService.java             # 业务接口
 │    └── impl
 │         └── UserServiceImpl.java    # 业务实现类
 │
 ├── mapper                            # 数据访问层（MyBatis Mapper 接口）
 │    └── UserMapper.java
 │
 ├── entity (或 pojo / model)           # 实体类与数据对象
 │    ├──  entity                          
 │    │    └── User.java                # 数据库实体类 (DO)
 │    ├── dto                          # 数据传输对象 (前端传来的参数)
 │    │    └── UserRegisterDTO.java
 │    └── vo                           # 视图对象 (返回给前端的数据)
 │         └── UserInfoVO.java
 │
 ├── common                  # 公共组件与核心基础类
 │    ├── Result.java                  # 全局统一返回结果封装（{code:, message:, data:} 用这样的格式传输）
 │    ├── constant                     # 常量包
 │    │    └── SystemConstants.java
 │    ├── exception                    # 异常处理包
 │    │    ├── GlobalExceptionHandler.java # 全局异常处理器
 │    │    └── BusinessException.java  # 自定义业务异常
 │    └── enums                        # 枚举类包
 │         └── OrderStatusEnum.java
 │
 ├── interceptor (或 filter)           # 拦截器/过滤器包
 │    └── JwtInterceptor.java          # JWT登录校验拦截器
 │
 └── utils                             # 工具类包
      ├── JwtUtil.java                 # JWT工具类
      └── RedisUtil.java               # Redis工具类