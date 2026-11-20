package com.hoopoe.zyfuntv.core.script

/**
 * JS 规则引擎接口占位。
 *
 * 源项目 zyfun 的 CatOpen(14) workerpool 脚本、DrpyJs0(2) JS0 引擎、部分 XBPQ 规则
 * 依赖在宿主侧执行一段 JavaScript 蜘蛛脚本（返回分类 / 列表 / 详情 / 播放地址）。
 *
 * 本期按既定决策**暂不引入 Rhino / QuickJS / V8**，因此 [available] 恒为 false，
 * 上层适配器命中 JS 型源时走静态规则容错或显式提示，不执行脚本。
 * 后续接入引擎时：实现本接口并在 [install] 注册即可，适配器侧无需改动。
 */
interface JsRunner {

    /** 是否具备可执行 JS 的运行时；未接入引擎时为 false。 */
    val available: Boolean

    /**
     * 在沙箱上下文中执行脚本并取其返回值。
     * @param script 蜘蛛脚本源码
     * @param entry 入口函数名（如 `category` / `home` / `detail` / `play`）
     * @param args 入参键值对
     * @return 脚本序列化后的字符串结果；不可用返回 null。
     */
    suspend fun eval(script: String, entry: String, args: Map<String, String>): String?

    companion object {
        @Volatile
        private var runner: JsRunner? = null

        fun install(runner: JsRunner) {
            this.runner = runner
        }

        fun current(): JsRunner = runner ?: Stub

        private object Stub : JsRunner {
            override val available: Boolean = false
            override suspend fun eval(script: String, entry: String, args: Map<String, String>): String? = null
        }
    }
}
