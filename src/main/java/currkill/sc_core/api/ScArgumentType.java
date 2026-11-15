package currkill.sc_core.api;

/** 指令参数的类型。加载器无关，Brigadier 适配层负责映射成具体 ArgumentType。 */
public enum ScArgumentType {
    /** 单个字符串，含空格时必须用引号。 */
    STRING,
    /** 单个单词，不能带空格、引号。 */
    WORD,
    /** 贪心字符串，吃掉后面所有字符，只能放在最后。 */
    GREEDY_STRING,
    INTEGER,
    DOUBLE,
    BOOLEAN
}
