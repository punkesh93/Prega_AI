package com.example.ui.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.BillingManager
import com.example.ui.components.*
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space

/**
 * Prega AI — Paywall.
 *
 * Written to be the opposite of a dark pattern. Specifically:
 *  - The price is Play's own localised string, never a hard-coded figure.
 *  - No countdown, no "3 spots left", no pre-ticked upsell, no fake discount.
 *  - Cancelling is explained *before* she pays, not buried after.
 *  - Declining is a plain, equally visible button — not grey 10pt text.
 *  - Nothing that matters medically sits behind the paywall. Kick counting,
 *    logging, appointments, milestones and safety guidance are free forever.
 *    Charging for the features that keep someone safe would be indefensible.
 */
@Composable
fun PaywallScreen(
    state: BillingManager.State,
    price: String?,
    error: String?,
    onSubscribe: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(PregaTheme.colors.dawnBrush)
            .systemBarsPadding()
            .padding(horizontal = Space.gutter),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(Space.xl))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(
                        "Not now",
                        style = MaterialTheme.typography.labelMedium,
                        color = PregaTheme.colors.inkMuted,
                    )
                }
            }

            Breathing { Text("🌷", fontSize = 56.sp) }

            Spacer(Modifier.height(Space.lg))
            Text(
                "Prega Premium",
                style = MaterialTheme.typography.displaySmall,
                color = PregaTheme.colors.ink,
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                "For the parts that take real thought — everything you need to stay safe stays free.",
                style = MaterialTheme.typography.bodyLarge,
                color = PregaTheme.colors.inkMuted,
            )

            Spacer(Modifier.height(Space.xxl))

            Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                Benefit(
                    "💬",
                    "Unlimited coach questions",
                    "Ask as much as you like, whenever it occurs to you — including at 3am.",
                )
                Benefit(
                    "🥗",
                    "Meal plans built around you",
                    "Weekly plans that respect your restrictions and work around how you actually feel.",
                )
                Benefit(
                    "📊",
                    "Your full history",
                    "Every kick session, mood and symptom charted over time — and exportable to take to an appointment.",
                )
                Benefit(
                    "🎨",
                    "Journey keepsakes",
                    "A shareable card for each milestone week, yours to keep.",
                )
            }

            Spacer(Modifier.height(Space.xxl))

            // Said plainly, before payment. Someone who knows how to leave is
            // more likely to stay.
            PregaCard(containerColor = PregaTheme.colors.recessed, border = false) {
                Text(
                    "Cancelling",
                    style = MaterialTheme.typography.titleMedium,
                    color = PregaTheme.colors.ink,
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    "Cancel any time from your Play Store subscriptions — it takes about " +
                        "fifteen seconds and you keep Premium until the period you've paid " +
                        "for ends. Everything you've logged stays yours either way.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PregaTheme.colors.inkMuted,
                )
            }

            Spacer(Modifier.height(Space.lg))

            PregaCard(border = true) {
                Text(
                    "Always free",
                    style = MaterialTheme.typography.titleMedium,
                    color = PregaTheme.colors.ink,
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    "Kick counting, daily logging, appointments, weekly milestones, quests, " +
                        "badges and all safety guidance. None of it will ever sit behind a payment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PregaTheme.colors.inkMuted,
                )
            }

            error?.let {
                Spacer(Modifier.height(Space.lg))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PregaTheme.colors.alert,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(Space.xl))
        }

        Column(Modifier.padding(bottom = Space.xl)) {
            when (state) {
                BillingManager.State.Connecting ->
                    PregaButton("Checking availability…", {}, enabled = false, loading = true)

                BillingManager.State.Unavailable ->
                    Column {
                        PregaButton("Unavailable right now", {}, enabled = false)
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "In-app purchases aren't available on this device or account. " +
                                "The free features all still work.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PregaTheme.colors.inkFaint,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                BillingManager.State.Ready ->
                    Column {
                        PregaButton(
                            text = price?.let { "Subscribe · $it a month" } ?: "Subscribe",
                            onClick = onSubscribe,
                            enabled = price != null,
                        )
                        Spacer(Modifier.height(Space.sm))
                        PregaTextButton("Maybe later", onDismiss, fillWidth = true)
                        Spacer(Modifier.height(Space.md))
                        Text(
                            "Billed through Google Play. Renews monthly until cancelled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PregaTheme.colors.inkFaint,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
            }
        }
    }
}

@Composable
private fun Benefit(emoji: String, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(PregaTheme.colors.cardSurface),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 19.sp) }

        Spacer(Modifier.width(Space.lg))

        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PregaTheme.colors.ink)
            Spacer(Modifier.height(Space.xxs))
            Text(body, style = MaterialTheme.typography.bodySmall, color = PregaTheme.colors.inkMuted)
        }
    }
}
