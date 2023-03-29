/**
 * The `foo` method without arguments
 * {@link foo:0}
 * {@link foo:function}
 * {@link foo:(0)}
 * {@link foo:function(0)}
 * {@link foo:NO_ARGS}
 * {@label NO_ARGS}
 */
function foo(): void;
/**
 * The `foo` method with a number argument
 * {@link foo:1}
 * {@link foo:function(1)}
 * {@link foo:(1)}
 * {@link foo:NUM_ARG}
 * {@label NUM_ARG}
 */
function foo(n: number): number;
/**
 * The `foo` method with a string argument
 * {@link foo:2}
 * {@link foo:function(2)}
 * {@link foo:(2)}
 * {@link foo:STR_ARG}
 * {@label STR_ARG}
 */
function foo<caret>(s: string): string;
function foo(a?: string | number): number | string | void {
    if (a === undefined) {
        return
    }

    return a
}
