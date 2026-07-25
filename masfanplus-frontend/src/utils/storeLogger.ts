/**
 * Store 数据操作日志记录工具
 * 提供统一规范的日志输出格式
 */

interface LogOptions {
  storeName?: string;
  action?: string;
  state?: Record<string, any>;
  payload?: any;
  result?: any;
  error?: any;
  timestamp?: Date;
}

export class StoreLogger {
  private static readonly PREFIX = '[STORE]';
  
  /**
   * 记录 Store 状态变化
   */
  static logStateChange(options: LogOptions): void {
    const {
      storeName,
      action,
      state,
      payload,
      result,
      error,
      timestamp = new Date()
    } = options;

    const logData = {
      timestamp: timestamp.toISOString(),
      store: storeName,
      action: action || 'unknown',
      ...(state !== undefined && { state }),
      ...(payload !== undefined && { payload }),
      ...(result !== undefined && { result }),
      ...(error !== undefined && { error: this.formatError(error) }),
    };

    if (error) {
      console.group(`${this.PREFIX} %cERROR ${storeName ? `(${storeName})` : ''}`, 'color: #ff4757; font-weight: bold;');
    } else {
      console.group(`${this.PREFIX} %c${action} ${storeName ? `(${storeName})` : ''}`, 'color: #3742fa; font-weight: bold;');
    }
    
    Object.entries(logData).forEach(([key, value]) => {
      if (key !== 'timestamp' && key !== 'store' && key !== 'action') {
        console.log(`%c${key}:`, 'font-weight: bold;', value);
      }
    });
    
    console.log('%cTimestamp:', 'font-weight: bold;', timestamp.toLocaleString());
    console.groupEnd();
  }

  /**
   * 记录 Store Action 执行
   */
  static logAction(options: LogOptions): void {
    const {
      storeName,
      action,
      payload,
      result,
      error,
      timestamp = new Date()
    } = options;

    const logData = {
      timestamp: timestamp.toISOString(),
      store: storeName,
      action: action || 'unknown',
      ...(payload !== undefined && { payload }),
      ...(result !== undefined && { result }),
      ...(error !== undefined && { error: this.formatError(error) }),
    };

    if (error) {
      console.group(`${this.PREFIX} %cACTION ERROR ${storeName ? `(${storeName})` : ''}`, 'color: #ff4757; font-weight: bold;');
    } else {
      console.group(`${this.PREFIX} %cACTION ${storeName ? `(${storeName})` : ''}`, 'color: #2ed573; font-weight: bold;');
    }
    
    Object.entries(logData).forEach(([key, value]) => {
      if (key !== 'timestamp' && key !== 'store' && key !== 'action') {
        console.log(`%c${key}:`, 'font-weight: bold;', value);
      }
    });
    
    console.log('%cTimestamp:', 'font-weight: bold;', timestamp.toLocaleString());
    console.groupEnd();
  }

  /**
   * 格式化错误信息
   */
  private static formatError(error: any): any {
    if (error instanceof Error) {
      const errorObj: any = {
        name: error.name,
        message: error.message,
        stack: error.stack
      };
      
      // 检查是否有 cause 属性
      if (error.hasOwnProperty('cause')) {
        errorObj.cause = (error as any).cause;
      }
      
      return errorObj;
    }
    return error;
  }

  /**
   * 记录简单的状态变更
   */
  static logSimple(storeName: string, action: string, data?: any): void {
    console.log(
      `${this.PREFIX} %c${action} %c(${storeName})`,
      'color: #ffa502; font-weight: bold;',
      'color: #747d8c; font-style: italic;',
      data || ''
    );
  }
}