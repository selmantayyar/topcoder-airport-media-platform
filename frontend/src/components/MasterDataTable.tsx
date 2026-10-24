import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  admin,
  ApiError,
  errorMessage,
  type MasterDataField,
  type MasterDataRow,
  type MasterDataSchema,
} from '../server/api';

/**
 * The one generic list + form for every master-data type. Everything — columns, inputs, option labels,
 * required markers, limits — comes from the schema the backend publishes, so adding a type needs no UI code.
 */
export function MasterDataTable({ schema, onChanged }: { schema: MasterDataSchema; onChanged?: () => void }) {
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

  const visible = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((r) => schema.fields.some((f) => display(f, r[f.name]).toLowerCase().includes(q)));
  }, [rows, filter, schema.fields]);

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
          setFormError(errorMessage(e, 'Could not save'));
        }
      })
      .finally(() => setSaving(false));
  };

  return (
    <div className="mdt">
      <div className="mdt__bar">
        <input
          type="search"
          placeholder={`Filter ${schema.label.toLowerCase()}…`}
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          aria-label={`Filter ${schema.label}`}
        />
        <span className="mdt__count">{visible.length} of {rows.length}</span>
        <button type="button" className="mdt__new" onClick={() => open(null)}>+ New</button>
      </div>

      <div className="mdt__scroll">
        <table className="mdt__table">
          <thead>
            <tr>
              {schema.fields.map((f) => <th key={f.name}>{f.label}</th>)}
              <th />
            </tr>
          </thead>
          <tbody>
            {visible.map((r) => (
              <tr key={r.id}>
                {schema.fields.map((f) => (
                  <td key={f.name} className={f.type === 'NUMBER' ? 'num' : ''}>{display(f, r[f.name])}</td>
                ))}
                <td><button type="button" onClick={() => open(r)}>Edit</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editing && (
        <div className="mdt__drawer" role="dialog" aria-label={`${editing.id == null ? 'New' : 'Edit'} ${schema.label}`}>
          <h4>{editing.id == null ? `New ${singular(schema.label)}` : `Edit ${singular(schema.label)} #${editing.id}`}</h4>
          {schema.fields.map((f) => (
            <label key={f.name} className="mdt__field">
              <span>{f.label}{f.required && f.type !== 'BOOLEAN' ? ' *' : ''}</span>
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
            <button type="button" onClick={() => setEditing(null)}>Cancel</button>
            <button type="button" className="mdt__save" onClick={save} disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
          </div>
        </div>
      )}
    </div>
  );
}

function FieldInput({ field, value, onChange }: { field: MasterDataField; value: unknown; onChange: (v: unknown) => void }) {
  switch (field.type) {
    case 'BOOLEAN':
      return <input type="checkbox" checked={Boolean(value)} onChange={(e) => onChange(e.target.checked)} />;
    case 'ENUM':
    case 'REF':
      return (
        <select value={value == null ? '' : String(value)} onChange={(e) => onChange(e.target.value)}>
          <option value="">— choose —</option>
          {field.options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
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

/** How a stored value reads in the table: option labels for ENUM/REF, yes/no for booleans. */
function display(field: MasterDataField, value: unknown): string {
  if (value == null || value === '') return '—';
  if (field.type === 'BOOLEAN') return value ? 'Yes' : 'No';
  if (field.type === 'ENUM' || field.type === 'REF') {
    return field.options.find((o) => o.value === String(value))?.label ?? String(value);
  }
  return String(value);
}

function blank(fields: MasterDataField[]): Record<string, unknown> {
  return Object.fromEntries(fields.map((f) => [f.name, f.type === 'BOOLEAN' ? false : null]));
}

function singular(label: string): string {
  return label.replace(/ & .*$/, '').replace(/s$/, '').toLowerCase();
}
