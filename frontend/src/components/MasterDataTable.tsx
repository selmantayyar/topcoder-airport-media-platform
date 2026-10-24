import { useCallback, useEffect, useState } from 'react';
import {
  admin,
  ApiError,
  errorMessage,
  type MasterDataField,
  type MasterDataRow,
  type MasterDataSchema,
} from '../server/api';
import { useI18n } from '../i18n/I18nProvider';

/**
 * The one generic list + form for every master-data type. Everything — columns, inputs, option labels,
 * required markers, limits — comes from the schema the backend publishes, so adding a type needs no UI code.
 * Labels are translated from the catalog when it knows them (`md.field.<name>`, `enum.<VALUE>`), else shown
 * as the backend wrote them.
 */
export function MasterDataTable({ schema, onChanged }: { schema: MasterDataSchema; onChanged?: () => void }) {
  const { t, label } = useI18n();
  const typeLabel = label('md.type', schema.type);
  const one = label('md.one', schema.type);
  const fieldLabel = (f: MasterDataField) => label('md.field', f.name, f.label);
  const show = (f: MasterDataField, value: unknown) => display(f, value, t('md.yes'), t('md.no'), label);
  const [rows, setRows] = useState<MasterDataRow[]>([]);
  const [filter, setFilter] = useState('');
  const [editing, setEditing] = useState<{ id: number | null; values: Record<string, unknown> } | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(() => {
    admin.rows(schema.type).then(setRows).catch(() => undefined);
  }, [schema.type]);

  useEffect(() => {
    load();
  }, [load]);

  const q = filter.trim().toLowerCase();
  const visible = q ? rows.filter((r) => schema.fields.some((f) => show(f, r[f.name]).toLowerCase().includes(q))) : rows;

  const open = (row: MasterDataRow | null) => {
    setFieldErrors({});
    setFormError(null);
    setEditing({ id: row?.id ?? null, values: row ? { ...row } : blank(schema.fields) });
  };

  const save = () => {
    if (!editing) return;
    setSaving(true);
    const request = editing.id == null
      ? admin.create(schema.type, editing.values)
      : admin.update(schema.type, editing.id, editing.values);
    request
      .then(() => {
        setEditing(null);
        load();
        onChanged?.();
      })
      .catch((e: unknown) => {
        if (e instanceof ApiError && e.body.fieldErrors.length > 0) {
          setFieldErrors(Object.fromEntries(e.body.fieldErrors.map((f) => [f.field, f.message])));
        } else {
          setFormError(errorMessage(e, t('md.saveFailed')));
        }
      })
      .finally(() => setSaving(false));
  };

  return (
    <div className="mdt">
      <div className="mdt__bar">
        <input
          type="search"
          placeholder={t('md.filter', { label: typeLabel.toLowerCase() })}
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          aria-label={t('md.filter', { label: typeLabel })}
        />
        <span className="mdt__count">{t('md.count', { shown: visible.length, total: rows.length })}</span>
        <button type="button" className="mdt__new" onClick={() => open(null)}>{t('md.new')}</button>
      </div>

      <div className="mdt__scroll">
        <table className="mdt__table">
          <thead>
            <tr>
              {schema.fields.map((f) => <th key={f.name}>{fieldLabel(f)}</th>)}
              <th />
            </tr>
          </thead>
          <tbody>
            {visible.map((r) => (
              <tr key={r.id}>
                {schema.fields.map((f) => (
                  <td key={f.name} className={f.type === 'NUMBER' ? 'num' : ''}>{show(f, r[f.name])}</td>
                ))}
                <td><button type="button" onClick={() => open(r)}>{t('md.edit')}</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editing && (
        <div className="mdt__drawer" role="dialog" aria-label={editing.id == null ? t('md.newTitle', { label: one }) : t('md.editTitle', { label: one, id: editing.id })}>
          <h4>{editing.id == null ? t('md.newTitle', { label: one }) : t('md.editTitle', { label: one, id: editing.id })}</h4>
          {schema.fields.map((f) => (
            <label key={f.name} className="mdt__field">
              <span>{fieldLabel(f)}{f.required && f.type !== 'BOOLEAN' ? ' *' : ''}</span>
              <FieldInput
                field={f}
                value={editing.values[f.name]}
                onChange={(v) => setEditing({ ...editing, values: { ...editing.values, [f.name]: v } })}
              />
              {fieldErrors[f.name] && <span className="field-error">{fieldErrors[f.name]}</span>}
            </label>
          ))}
          {formError && <p className="field-error">{formError}</p>}
          <div className="mdt__actions">
            <button type="button" onClick={() => setEditing(null)}>{t('md.cancel')}</button>
            <button type="button" className="mdt__save" onClick={save} disabled={saving}>{saving ? t('md.saving') : t('md.save')}</button>
          </div>
        </div>
      )}
    </div>
  );
}

function FieldInput({ field, value, onChange }: { field: MasterDataField; value: unknown; onChange: (v: unknown) => void }) {
  const { t, label } = useI18n();
  switch (field.type) {
    case 'BOOLEAN':
      return <input type="checkbox" checked={Boolean(value)} onChange={(e) => onChange(e.target.checked)} />;
    case 'ENUM':
    case 'REF':
      return (
        <select value={value == null ? '' : String(value)} onChange={(e) => onChange(e.target.value)}>
          <option value="">{t('md.choose')}</option>
          {field.options.map((o) => <option key={o.value} value={o.value}>{optionLabel(o, label)}</option>)}
        </select>
      );
    case 'NUMBER':
      return (
        <input
          type="number"
          value={value == null ? '' : String(value)}
          min={field.min ?? undefined}
          max={field.max ?? undefined}
          step="any"
          onChange={(e) => onChange(e.target.value)}
        />
      );
    default:
      return (
        <input
          type="text"
          value={value == null ? '' : String(value)}
          maxLength={field.maxLength ?? undefined}
          onChange={(e) => onChange(e.target.value)}
        />
      );
  }
}

type Label = (group: string, value: string, fallback?: string) => string;

/** An ENUM option in the UI language; REF options are names, not in the catalog, and stay as they are. */
function optionLabel(option: MasterDataField['options'][number], label: Label): string {
  return label('enum', option.value, option.label);
}

/** How a stored value reads in the table: option labels for ENUM/REF, yes/no for booleans. */
function display(field: MasterDataField, value: unknown, yes: string, no: string, label: Label): string {
  if (value == null || value === '') return '—';
  if (field.type === 'BOOLEAN') return value ? yes : no;
  if (field.type === 'ENUM' || field.type === 'REF') {
    const option = field.options.find((o) => o.value === String(value));
    return option ? optionLabel(option, label) : String(value);
  }
  return String(value);
}

function blank(fields: MasterDataField[]): Record<string, unknown> {
  return Object.fromEntries(fields.map((f) => [f.name, f.type === 'BOOLEAN' ? false : null]));
}
