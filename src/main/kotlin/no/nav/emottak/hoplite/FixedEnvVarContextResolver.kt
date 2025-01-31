package no.nav.emottak.hoplite

import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.fp.valid

/**
 * Replaces strings of the form ${{ env:name }} by looking up the name as an environment variable.
 * Defaults can also be applied in case the env var does not exist: ${{ env:name :- default }}.
 * This can be combined with f.ex. reference to another config-variable (in lowercase):
 * ${{ env:AUTH_PORT :- ${{ref:auth.mockport :- 3344}} }}
 */
object FixedEnvVarContextResolver : FixedContextResolver() {

    override val contextKey: String = "env"
    override val default: Boolean = true

    override fun lookup(path: String, node: StringNode, root: Node, context: DecoderContext): ConfigResult<String?> {
        return System.getenv(path).valid()
    }
}
