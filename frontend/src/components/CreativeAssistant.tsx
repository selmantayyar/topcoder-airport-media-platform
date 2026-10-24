import { useState } from 'react';
import { errorMessage, retailers, type AdPage, type CreativeSummary, type GeneratedCreative } from '../server/api';

interface Props {
  advertiserId: number;
  /** Called after pages are saved, so the library refreshes. */
  onGenerated: () => void;
  onUseCreative: (creative: CreativeSummary) => void;
}

const EXAMPLE = 'Festive sweets, 20% off, families flying domestic';

/**
 * The GenAI creative assistant: a one-line brief becomes a ready ad page in English and Spanish, saved to
 * the library in the standard template. Copy that cites a fact the brief never stated is rejected server-side.
 */
export function CreativeAssistant({ advertiserId, onGenerated, onUseCreative }: Props) {
  const [brief, setBrief] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<GeneratedCreative | null>(null);
  const [error, setError] = useState<string | null>(null);

  const generate = () => {
    setBusy(true);
    setError(null);
    retailers
      .generateCreative(advertiserId, brief.trim() || EXAMPLE)
      .then((r) => {
        setResult(r);
        onGenerated();
      })
      .catch((e: unknown) => setError(errorMessage(e, 'Could not write an ad from this brief')))
      .finally(() => setBusy(false));
  };

  return (
    <div className="assistant">
      <div className="assistant__brief">
        <label>
          Brief
          <input
            value={brief}
            maxLength={300}
            placeholder={EXAMPLE}
            onChange={(e) => setBrief(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && !busy && generate()}
          />
        </label>
        <button type="button" className="assistant__go" onClick={generate} disabled={busy}>
          {busy ? 'Writing…' : 'Generate ad page'}
        </button>
      </div>
      {error && <span className="field-error">{error}</span>}
      {result && (
        <>
          <p className="builder__hint">
            Written by the <strong>{result.provider === 'llm' ? 'LLM' : 'offline mock'}</strong> assistant and saved to
            your library.
            {result.notes.map((n) => (
              <span key={n} className="assistant__note"> {n}.</span>
            ))}
          </p>
          <div className="assistant__pages">
            {result.pages.map((p) => (
              <div key={p.creativeId} className="assistant__page">
                <div className="mock-screen" aria-label={`${p.language} ad page`}>
                  <div className="ad">
                    <span className="ad__brand">{p.language.toUpperCase()}</span>
                    <h1 className="ad__headline">{p.headline}</h1>
                    {p.offerText && <p className="ad__offer">{p.offerText}</p>}
                    {p.body && <p className="ad__body">{p.body}</p>}
                  </div>
                </div>
                <button type="button" onClick={() => onUseCreative(toSummary(p))}>Use in new campaign</button>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function toSummary(p: AdPage): CreativeSummary {
  return {
    id: p.creativeId,
    campaignId: null,
    campaignName: null,
    headline: p.headline,
    offerText: p.offerText,
    body: p.body,
    language: p.language,
    templateKey: p.templateKey,
  };
}
