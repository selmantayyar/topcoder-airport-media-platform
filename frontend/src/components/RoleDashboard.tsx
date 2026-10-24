import type { RoleId } from '../roles';
import { AdminConsole } from './AdminConsole';
import { AirlineView } from './AirlineView';
import { MediaManagerView } from './MediaManagerView';
import { RetailerWorkspace } from './RetailerWorkspace';
import { ScreenChooser } from './ScreenChooser';

/** The main view for one persona — shared by the normal layout and the split-screen demo. */
export function RoleDashboard({ role }: { role: RoleId }) {
  switch (role) {
    case 'admin':
      return <AdminConsole />;
    case 'retailer':
      return <RetailerWorkspace />;
    case 'media-manager':
      return <MediaManagerView />;
    case 'airline':
      return <AirlineView />;
    case 'passenger':
      return <ScreenChooser />;
  }
}
