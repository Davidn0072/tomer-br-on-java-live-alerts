import type { ConnectionStatus as Status } from '../hooks/useAlertSocket';

const LABELS: Record<Status, string> = {
  connecting: 'Connecting…',
  open: 'Live',
  reconnecting: 'Reconnecting…',
};

export function ConnectionStatus({ status }: { status: Status }) {
  return (
    <span className={`connection-status connection-status--${status}`} role="status">
      <span className="connection-status__dot" aria-hidden="true" />
      {LABELS[status]}
    </span>
  );
}
