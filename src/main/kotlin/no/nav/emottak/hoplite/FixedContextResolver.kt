package no.nav.emottak.hoplite

import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.fp.flatMap
import com.sksamuel.hoplite.fp.invalid
import com.sksamuel.hoplite.fp.valid
import com.sksamuel.hoplite.resolver.Resolver
import com.sksamuel.hoplite.resolver.context.ContextResolverMode
import kotlin.reflect.KClass

abstract class FixedContextResolver : Resolver {
    abstract val contextKey: String
    abstract val default: Boolean

    // this regex will match most nested replacements first (inside to outside)
    // redundant escaping required for Android support
    private fun contextRegex() = "\\$\\{\\{\\s*$contextKey:([^{}]*)\\}\\}".toRegex()

    // this regex will match if the context is a prefex
    // redundant escaping required for Android support
    private fun prefixRegex() = "$contextKey://(.*)".toRegex()

    // Fix nr 1: Opprinnelig uttrykk "(.+):-(.+)" støtter ikke at defaultverdi kan være blank
    // private val valueWithDefaultRegex = "(.+):-(.+)".toRegex()
    private val valueWithDefaultRegex = "(.+):-(.*)".toRegex()

    override suspend fun resolve(paramName: String?, kclass: KClass<*>, node: Node, root: Node, context: DecoderContext): ConfigResult<Node> {
        return when (node) {
            is StringNode -> resolve(node, root, context)
            else -> node.valid()
        }
    }

    private fun resolve(node: StringNode, root: Node, context: DecoderContext): ConfigResult<StringNode> {
        val result = prefixRegex().matchEntire(node.value) ?: contextRegex().find(node.value) ?: return node.valid()
        val path = result.groupValues[1].trim()

        val matchWithDefault = valueWithDefaultRegex.matchEntire(path)
        val replacement = when {
            matchWithDefault == null -> lookup(path.trim(), node, root, context)
            !default -> lookup(path.trim(), node, root, context)
            // default value provided, so we use the first component with second component fallback
            else -> lookupWithFallback(
                matchWithDefault.groupValues[1].trim(),
                matchWithDefault.groupValues[2].trim(),
                node,
                root,
                context
            )
        }

        return replacement.flatMap {
            when {
                it == null && context.contextResolverMode == ContextResolverMode.SkipUnresolved -> node.valid()
                it == null -> ConfigFailure.ResolverFailure("Could not resolve '$path'").invalid()
                else -> node.copy(value = node.value.replaceRange(result.range, it)).valid()
            }
        }
    }

    private fun lookupWithFallback(
        path: String,
        fallback: String,
        node: StringNode,
        root: Node,
        context: DecoderContext
    ) = lookup(path, node, root, context).flatMap {
        // Fix nr 2: Fikk aldri den statiske default-verdien (f.eks. "http://locahost:9092")
        // fordi det opprinnelig bare ble gjort et nytt lookup-kall som da alltid returnerte Valid(value=null)
        // it?.valid() ?: lookup(fallback, node, root, context) }
        it?.valid() ?: if (fallback.contains("\${{")) lookup(fallback, node, root, context) else fallback.valid()
    }

    /**
     * Return the replacement value, or null if no replacement should take place.
     */
    abstract fun lookup(path: String, node: StringNode, root: Node, context: DecoderContext): ConfigResult<String?>
}
